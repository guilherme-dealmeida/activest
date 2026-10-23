package com.activest.domain.issuer;

/**
 * Plano de contas da companhia na CVM, identificado pelo nome da conta 3.01 do
 * DRE (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.1). Preenchido pela importacao de balancos (F5).
 */
public enum StatementLayout {
    COMERCIAL,
    BANCO,
    SEGURADORA
}
