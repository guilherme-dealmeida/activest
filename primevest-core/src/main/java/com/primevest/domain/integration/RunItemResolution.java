package com.primevest.domain.integration;

/** O que a confirmacao fez com o item (V26, ck_integration_run_items_resolution). */
public enum RunItemResolution {
    /** Virou transacao. */
    IMPORTED,
    /** Virou transacao e o lancamento manual duplicado saiu. */
    REPLACED,
    /** Virou transacao e o lancamento manual ficou. */
    KEPT_BOTH,
    /** Nao entrou: duplicata ignorada, ou ja importado/excluido nesse meio tempo. */
    SKIPPED
}
