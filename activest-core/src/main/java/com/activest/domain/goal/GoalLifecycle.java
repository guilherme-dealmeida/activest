package com.activest.domain.goal;

/**
 * Ciclo de vida da meta (docs/planos/PLANO_METAS.md, Anexo A 7.2): quem muda e o usuario; {@code CONCLUIDA} tambem o
 * sistema, quando a meta crescente atinge o alvo (D13). Valores gravados em {@code goals.lifecycle}.
 */
public enum GoalLifecycle {
    ATIVA,
    PAUSADA,
    CONCLUIDA,
    ARQUIVADA
}
