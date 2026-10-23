package com.primevest.domain.task;

/**
 * Status de uma rodada (D2: em inglês no banco e na tela). {@code SCHEDULED} não é gravado: a agendada é calculada do
 * cron na leitura (D3).
 */
public enum TaskExecutionStatus {
    RUNNING,
    SUCCEEDED,
    FAILED,
    /** Encerrada pelo Stop (parada cooperativa, D8). */
    CANCELLED,
    /** Chegou com outra rodada do mesmo tipo em execução (D20, D25). */
    SKIPPED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
