package com.activest.domain.integration;

/**
 * Ciclo de uma sincronizacao (V25, ck_integration_runs_status).
 *
 * <p>QUEUED -> RUNNING -> AWAITING_CONFIRMATION (pre-visualizacao) ->
 * SUCCEEDED; ou FAILED / CANCELLED.</p>
 */
public enum RunStatus {
    QUEUED,
    RUNNING,
    AWAITING_CONFIRMATION,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
