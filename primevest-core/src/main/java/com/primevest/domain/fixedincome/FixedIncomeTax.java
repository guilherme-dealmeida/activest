package com.primevest.domain.fixedincome;

/** Os dois impostos do resgate de renda fixa e do Tesouro (V52, docs/planos/PLANO_VENDA_RENDA_FIXA.md). */
public enum FixedIncomeTax {
    /** Imposto de renda retido na fonte, regressivo pelo prazo do aporte. */
    IR,
    /** IOF do resgate antes de 30 dias, regressivo pelo dia. */
    IOF
}
