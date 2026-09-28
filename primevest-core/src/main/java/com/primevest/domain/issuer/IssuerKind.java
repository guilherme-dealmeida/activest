package com.primevest.domain.issuer;

/** Tipo de emissor (V28). BDR, ETF e demais tipos nao tem emissor. */
public enum IssuerKind {
    /** Companhia aberta: balanco na CVM (DFP/ITR). */
    COMPANY,
    /** Fundo imobiliario: informe mensal na CVM, avisos de rendimento no FNET. */
    FII
}
