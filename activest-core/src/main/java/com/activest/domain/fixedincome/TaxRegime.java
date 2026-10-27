package com.activest.domain.fixedincome;

/**
 * Regime de imposto de uma linha de {@code fixed_income_tax_rules} (V12, docs/planos/PLANO_FUNDOS_INVESTIMENTO.md,
 * C8 e 11.3).
 *
 * <p>{@link #GENERAL} são as linhas da renda fixa e do Tesouro (V52) — o IOF regressivo delas também vale para os
 * fundos de longo e de curto prazo. Os demais são dos fundos de investimento. Fundo de previdência não tem regime: vale
 * bruto (o IR é do plano PGBL/VGBL, não do fundo — D21).</p>
 */
public enum TaxRegime {
    /** Renda fixa e Tesouro Direto (e o IOF dos fundos). */
    GENERAL,
    /** Fundo de longo prazo: IR 22,5% a 15% pelo prazo, come-cotas 15%. */
    FUND_LONG_TERM,
    /** Fundo de curto prazo: IR 22,5% até 180 dias e 20% depois, come-cotas 20%. */
    FUND_SHORT_TERM,
    /** Fundo de ações: IR 15% no resgate, sem come-cotas e sem IOF. */
    FUND_EQUITY
}
