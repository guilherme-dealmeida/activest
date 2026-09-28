package com.primevest.domain.asset;

/**
 * Índice de remuneração de um título de renda fixa.
 *
 * <p>Nulo quando {@link RateType#PREFIXADO}, e também para renda variável,
 * que não tem remuneração indexada.</p>
 */
public enum Indexer {
    /** Certificado de Depósito Interbancário. */
    CDI,
    /** Taxa básica de juros. */
    SELIC,
    /** Índice Nacional de Preços ao Consumidor Amplo. */
    IPCA,
    /** Índice Geral de Preços do Mercado. */
    IGPM,
    /** Índice Nacional de Preços ao Consumidor. */
    INPC,
    /** Taxa Referencial. */
    TR
}
