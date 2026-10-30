package com.activest.domain.asset;

/**
 * Bolsas dos EUA do catálogo do exterior (D1 — docs/planos/PLANO_ATIVOS_INTERNACIONAIS.md). Valores de registro
 * ({@code foreign_listings.exchange}).
 */
public enum ForeignExchange {
    NYSE,
    NASDAQ,
    NYSE_AMERICAN,
    NYSE_ARCA,
    /** Cboe BZX. */
    CBOE,
    IEX;

    /**
     * O código de bolsa da lista do Nasdaq Trader ({@code Listing Exchange}): A = NYSE American, N = NYSE, P = NYSE Arca,
     * Z = Cboe BZX, V = IEX, Q = Nasdaq. Outro código (bolsa fora da D1): nulo.
     */
    public static ForeignExchange ofNasdaqTraderCode(String code) {
        if (code == null) {
            return null;
        }
        return switch (code.trim()) {
            case "A" -> NYSE_AMERICAN;
            case "N" -> NYSE;
            case "P" -> NYSE_ARCA;
            case "Z" -> CBOE;
            case "V" -> IEX;
            case "Q" -> NASDAQ;
            default -> null;
        };
    }
}
