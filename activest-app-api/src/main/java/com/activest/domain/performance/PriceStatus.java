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
    /**
     * Preco carregado (revisao de 25/10/2026, PLANO_METAS.md 5.9 opcao a): o ultimo fechamento conhecido depois de
     * {@link PortfolioTimeline#MAX_STALE_DAYS} pregoes sem negocio; sem nenhum fechamento anterior, o preco da compra;
     * renda fixa sem curva, o valor aplicado (como a Carteira). O dia continua disponivel; a tela avisa.
     */
    CARRIED,
    /** Sem nenhum numero (nem preco anterior nem preco de compra): o dia fica indisponivel no escopo da posicao. */
    MISSING
}
