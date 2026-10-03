package com.primevest.domain.integration;

/**
 * O destino de cada movimento de uma execucao (V26,
 * ck_integration_run_items_outcome). Mesmos nomes de
 * {@link com.primevest.domain.integration.pipeline.PreviewOutcome}, mais FAILED.
 */
public enum RunItemOutcome {
    /** Vira transacao ao confirmar. */
    NEW,
    /** Ja importado por esta conexao. */
    EXISTING,
    /** O usuario excluiu esta operacao: nao volta. */
    EXCLUDED,
    /** Regras R6/R7/R8, transferencias, rendimentos: nao importado, com motivo. */
    UNMAPPED,
    /** Parece com um lancamento manual (6.6): aguarda a decisao do usuario. */
    POSSIBLE_DUPLICATE,
    /** A regra de negocio recusou a gravacao. */
    FAILED
}
