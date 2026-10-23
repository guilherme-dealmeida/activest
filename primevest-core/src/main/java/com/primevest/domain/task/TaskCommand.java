package com.primevest.domain.task;

import java.util.UUID;

/**
 * Comando entre os serviços pelo Redis (docs/planos/PLANO_TAREFAS.md, D29): {@code RELOAD} = as tarefas do serviço
 * mudaram, reagendar; {@code RUN} = o Run da tela numa tarefa do outro serviço. O Stop não passa por aqui: vai pelo
 * banco ({@code stop_requested_at}).
 */
public record TaskCommand(Action action, TaskApi service, Long taskId, UUID requestedBy) {

    public static final String CHANNEL = "primevest:tasks:commands";

    public enum Action {
        RELOAD,
        RUN
    }

    public static TaskCommand reload(TaskApi service) {
        return new TaskCommand(Action.RELOAD, service, null, null);
    }

    public static TaskCommand run(TaskApi service, Long taskId, UUID requestedBy) {
        return new TaskCommand(Action.RUN, service, taskId, requestedBy);
    }
}
