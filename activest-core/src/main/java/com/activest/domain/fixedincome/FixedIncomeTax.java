package com.activest.domain.fixedincome;

/** Os impostos do resgate de renda fixa e do Tesouro (V52) e o come-cotas dos fundos (V12). */
public enum FixedIncomeTax {
    /** Imposto de renda retido na fonte, regressivo pelo prazo do aporte. */
    IR,
    /** IOF do resgate antes de 30 dias, regressivo pelo dia. */
    IOF,
    /** Come-cotas dos fundos de investimento: IR antecipado em maio e novembro (V12, PLANO_FUNDOS_INVESTIMENTO.md). */
    CC
}
