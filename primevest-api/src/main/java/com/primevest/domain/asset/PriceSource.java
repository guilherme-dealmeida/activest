package com.primevest.domain.asset;

/**
 * De onde vem a cotação de um tipo de ativo.
 *
 * <p>Existe para que o roteamento entre provedores seja dado no banco, e não
 * comparação de string no código. Antes o scheduler decidia por
 * {@code "CRYPTO".equals(assetType.getName())}, o que quebrava em silêncio a
 * cada renomeação ou tipo novo.</p>
 */
public enum PriceSource {
    /** Cotação da B3 via BrAPI: ações, FIIs, ETFs e BDRs. */
    BRAPI,
    /** Criptomoedas via CoinGecko. */
    COINGECKO,
    /** Sem provedor automático: renda fixa, fundos e ativos dos EUA
     *  enquanto não houver um cliente para eles. */
    MANUAL
}
