package com.primevest.domain.task;

/**
 * A rotina não rodou porque a mesma rotina já está em execução por outro caminho (ex.: a carga inicial segurando os
 * proventos): a rodada termina {@code SKIPPED} com o motivo (D20), e não {@code SUCCEEDED} sem ter feito nada.
 */
public class TaskSkippedException extends RuntimeException {

    public TaskSkippedException(String reason) {
        super(reason);
    }
}
