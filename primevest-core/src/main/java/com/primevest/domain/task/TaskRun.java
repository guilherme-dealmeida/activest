package com.primevest.domain.task;

import org.slf4j.MDC;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * A rodada da thread atual (docs/planos/PLANO_TAREFAS.md, T4). O executor liga a rodada à thread que roda a rotina;
 * os services chamam {@link #checkStop()} entre unidades de trabalho sem receber o contexto — fora de uma rodada (o
 * pedido sob demanda, um teste) a chamada não faz nada. {@link #propagate} e {@link #propagateCall} leva a rodada e o MDC às threads que a
 * rotina abre, para o Stop e o log da rodada (D6) valerem nelas também.
 */
public final class TaskRun {

    private static final ThreadLocal<TaskRunContext> CURRENT = new ThreadLocal<>();

    private TaskRun() {
    }

    static void bind(TaskRunContext ctx) {
        CURRENT.set(ctx);
    }

    static void unbind() {
        CURRENT.remove();
    }

    public static Optional<TaskRunContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Ponto de parada (D8): com o Stop pedido, encerra a rodada aqui ({@link TaskStoppedException}). */
    public static void checkStop() {
        TaskRunContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.checkStop();
        }
    }

    public static Runnable propagate(Runnable task) {
        TaskRunContext ctx = CURRENT.get();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (ctx == null && mdc == null) {
            return task;
        }
        return () -> {
            TaskRunContext previous = CURRENT.get();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            apply(ctx, mdc);
            try {
                task.run();
            } finally {
                apply(previous, previousMdc);
            }
        };
    }

    public static <T> Callable<T> propagateCall(Callable<T> task) {
        TaskRunContext ctx = CURRENT.get();
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        if (ctx == null && mdc == null) {
            return task;
        }
        return () -> {
            TaskRunContext previous = CURRENT.get();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            apply(ctx, mdc);
            try {
                return task.call();
            } finally {
                apply(previous, previousMdc);
            }
        };
    }

    private static void apply(TaskRunContext ctx, Map<String, String> mdc) {
        if (ctx == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(ctx);
        }
        if (mdc == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(mdc);
        }
    }
}
