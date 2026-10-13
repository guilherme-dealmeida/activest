package com.primevest.domain.transaction;

import java.util.function.Supplier;

/**
 * Marca as escritas que o próprio PrimeVest faz em nome do usuário — a liquidação automática do título vencido
 * (docs/planos/PLANO_NOTIFICACOES.md, seção 6). Dentro dela a transação nasce com origem SISTEMA, a edição não ganha
 * a marca de "editada pelo usuário" e nada dispara a conferência de novo.
 */
public final class SystemWrites {

    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private SystemWrites() {
    }

    public static boolean active() {
        return ACTIVE.get();
    }

    public static <T> T run(Supplier<T> work) {
        boolean before = ACTIVE.get();
        ACTIVE.set(true);
        try {
            return work.get();
        } finally {
            ACTIVE.set(before);
        }
    }
}
