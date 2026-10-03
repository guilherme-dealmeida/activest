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
    B3_COTAHIST
}
