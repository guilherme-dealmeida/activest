package com.activest.domain.marketindex;

/**
 * De onde vem a serie: {@code source_ref} e o codigo da serie no SGS, a sigla do indice na B3 ou, na curva de juros do
 * Tesouro, {@code <curva>:<anos>} ({@link YieldCurve}).
 */
public enum IndexSource {
    BCB_SGS,
    B3,
    /** Curva de juros calculada do arquivo do Tesouro Direto (docs/planos/PLANO_JUROS_FUTUROS.md). */
    TESOURO
}
