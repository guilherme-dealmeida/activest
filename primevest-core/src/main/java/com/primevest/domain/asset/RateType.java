package com.primevest.domain.asset;

/**
 * Como um título de renda fixa remunera.
 *
 * <p>Sem esta informação não é possível distinguir "110% do CDI" de "CDI + 2%":
 * ambos teriam {@code indexer = CDI} e um número em {@code indexerRate}, com
 * sentidos completamente diferentes.</p>
 */
public enum RateType {
    /** Taxa fixa, sem índice. {@code indexer} nulo, {@code indexerRate} = % a.a. */
    PREFIXADO,
    /** Percentual de um índice. Ex: {@code CDI} com {@code indexerRate = 110} (% do índice). */
    POS_FIXADO,
    /** Índice mais um spread. Ex: {@code IPCA} com {@code indexerRate = 6} (spread % a.a.). */
    HIBRIDO
}
