package com.activest.domain.integration.pipeline;

/** O destino de cada movimento na pre-visualizacao (6.13). */
public enum PreviewOutcome {
    /** Vira transacao ao confirmar. */
    NEW,
    /** Ja foi importado por esta conexao (mesmo externalId). */
    EXISTING,
    /** O usuario excluiu esta operacao (integration_crypto_exclusions): nao volta. */
    EXCLUDED,
    /** Nao importado, com o motivo (R6, R7, R8, transferencias, rendimentos). */
    UNMAPPED,
    /**
     * Viraria transacao, mas parece com um lancamento manual do usuario (6.6):
     * a confirmacao exige a decisao dele (substituir, manter os dois, ignorar).
     */
    POSSIBLE_DUPLICATE
}
