package com.primevest.domain.pricehistory;


/**
 * De onde veio o pregao gravado em {@code asset_price_history} (V29).
 * Dia gravado pela B3 nao e sobrescrito pela BRAPI.
 */
public enum PriceHistorySource {
    /** Fechamento diario dos ativos em carteira (ClosingPriceService, adm-api). */
    BRAPI,
    /** Fechamento diario de cripto. */
    COINGECKO,
    /** Serie historica oficial da B3 (COTAHIST), carregada pelo admin desde 2010. */
    B3_COTAHIST,
    /** PU de venda do Tesouro Direto (Tesouro Transparente, V44). */
    TESOURO,
    /**
     * Historico de cripto da Binance (klines diario publico, V58 — docs/planos/PLANO_RENTABILIDADE.md, D2). So
     * preenche datas sem linha: nunca sobrescreve a CoinGecko.
     */
    BINANCE
}
