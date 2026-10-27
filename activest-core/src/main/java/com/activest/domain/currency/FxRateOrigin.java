package com.activest.domain.currency;

/** De onde veio o câmbio de uma transação (V9). */
public enum FxRateOrigin {
    /** Moeda base: câmbio 1, sem campo na tela (D5). */
    BASE,
    /** PTAX venda da data (padrão, D5/D16). */
    PTAX,
    /** Informado pelo usuário. */
    INFORMED
}
