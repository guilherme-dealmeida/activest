package com.primevest.domain.task;

/** O Stop foi pedido e a rotina parou num ponto seguro (D8): a rodada termina {@code CANCELLED}. */
public class TaskStoppedException extends RuntimeException {

    public TaskStoppedException() {
        super("Encerrada a pedido");
    }
}
