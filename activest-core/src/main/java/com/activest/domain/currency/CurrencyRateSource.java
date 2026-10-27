package com.activest.domain.currency;

/** De onde vem o câmbio da moeda (V9). {@code NONE} só na moeda base. */
public enum CurrencyRateSource {
    NONE,
    /** API PTAX do Banco Central (Olinda), boletim de fechamento — D7. */
    BCB_PTAX
}
