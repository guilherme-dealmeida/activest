package com.activest.domain.goal;

/** O que aconteceu com a meta (docs/planos/PLANO_METAS.md, Anexo A 9.1): {@code goal_events.kind}. */
public enum GoalEventKind {
    CRIADA,
    EDITADA,
    PAUSADA,
    RETOMADA,
    CONCLUIDA,
    ARQUIVADA,
    DESARQUIVADA,
    ATINGIDA,
    SAIU_DO_OBJETIVO,
    VOLTOU_AO_OBJETIVO,
    PRAZO_VENCIDO
}
