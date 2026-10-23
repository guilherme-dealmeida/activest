package com.activest.domain.integration;

/** O que disparou a sincronizacao (V25, ck_integration_runs_trigger). */
public enum RunTrigger {
    /** Botao "Sincronizar". */
    MANUAL,
    /** Rotina diaria. */
    SCHEDULED,
    /** A primeira, disparada ao conectar. */
    CONNECTION
}
