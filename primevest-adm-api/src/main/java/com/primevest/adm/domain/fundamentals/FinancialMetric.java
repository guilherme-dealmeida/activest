package com.primevest.adm.domain.fundamentals;

/**
 * Metricas gravadas de cada DFP/ITR (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 1.1), em reais
 * (escala da CVM aplicada) — exceto o LPA, em reais por acao.
 */
public enum FinancialMetric {
    RECEITA,
    /** Resultado antes do resultado financeiro e dos tributos. Nao existe em banco. */
    EBIT,
    LUCRO_LIQUIDO,
    LUCRO_CONTROLADORA,
    ATIVO_TOTAL,
    /** Caixa e equivalentes + aplicacoes financeiras. So no layout comercial. */
    CAIXA,
    /** Emprestimos e financiamentos, circulante + nao circulante. So no layout comercial. */
    DIVIDA_BRUTA,
    PL,
    PL_NAO_CONTROLADORES,
    FCO,
    JCP_DECLARADO,
    DIVIDENDOS_DECLARADOS,
    /** LPA basico informado pela companhia, por classe. So conferencia (ver ShareScale). */
    LPA_ON,
    LPA_PN;

    public boolean perShare() {
        return this == LPA_ON || this == LPA_PN;
    }
}
