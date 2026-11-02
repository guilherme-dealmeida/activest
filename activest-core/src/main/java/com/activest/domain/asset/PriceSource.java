package com.activest.domain.asset;

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
    /** Tesouro Direto: PU de venda publicado pelo Tesouro (Tesouro Transparente), V44. */
    TESOURO,
    /** Sem provedor automático: renda fixa e outros. */
    MANUAL,
    /** Fundos de investimento: cota do informe diário da CVM (V12, docs/planos/PLANO_FUNDOS_INVESTIMENTO.md). */
    CVM,
    /**
     * Ativos dos EUA (Stock, REIT e ETF internacional): fechamento do Massive grouped daily (V19,
     * docs/planos/PLANO_PRECOS_INTERNACIONAIS.md).
     */
    MASSIVE
}
