package com.activest.domain.performance;

/**
 * Situacao do preco de uma posicao num dia da linha do tempo (docs/planos/PLANO_RENTABILIDADE.md, R10).
 */
public enum PriceStatus {
    /** Preco do proprio dia (ou o atual, no ponto de hoje). */
    OK,
    /** Sem negocio no dia: ultimo fechamento de ate {@link PortfolioTimeline#MAX_STALE_DAYS} pregoes antes (D3). */
    STALE,
    /** Renda fixa com indice do mes ainda nao publicado, estimado pelo ultimo (D5 da valorizacao). */
    ESTIMATED,
    /** Sem preco: o dia fica indisponivel no escopo que contem a posicao. */
    MISSING
}
