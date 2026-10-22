package com.primevest.domain.task;

/** Origem da rodada. */
public enum TaskTriggerType {
    /** Horário (cron da tarefa). */
    SCHEDULE,
    /** Pedido na tela ou nos endpoints de administrador. */
    MANUAL,
    /** Recuperação na subida do serviço. */
    STARTUP
}
