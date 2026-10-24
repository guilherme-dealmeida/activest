package com.activest.adm.domain.fundamentals;

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
    /**
     * Dividendos + JCP <b>pagos</b> aos acionistas da controladora no periodo (DFC, atividades de financiamento),
     * positivo. Base do payout desde 10/10/2026 (docs/planos/PLANO_PRECO_TETO.md, 1.5 e D4): a DVA de 167 de 357
     * companhias no DFP 2025 traz dividendos zerados (Vale: tudo em lucros retidos). Com DFC e sem conta de
     * dividendos, 0 (nao pagou).
     */
    DIVIDENDOS_PAGOS,
    // Contas novas (docs/planos/PLANO_TELA_ATIVOS.md, 2.5, 23/10/2026): valores gravados em portugues, como os demais
    /** Resultado bruto (DRE 3.03). So no layout comercial. */
    LUCRO_BRUTO,
    /**
     * Depreciacao e amortizacao, positiva = despesa: a DVA (retencoes, "depreciacao, amortizacao e exaustao") menos a
     * {@link #EXAUSTAO_BIOLOGICA} quando a companhia a informa a parte na DFC (D22, 24/10/2026 — como o Investidor10).
     * So no layout comercial. Base do EBITDA.
     */
    DEPRECIACAO_AMORTIZACAO,
    /**
     * Exaustao dos ativos biologicos (florestas), informada em linha propria nos ajustes da DFC (6.01.01.*), positiva.
     * Sai do EBITDA (D22). Gravada para conferencia; so no layout comercial.
     */
    EXAUSTAO_BIOLOGICA,
    /** Resultado antes dos tributos sobre o lucro (DRE). Base da aliquota do ROIC. */
    LAIR,
    /** Imposto de renda e contribuicao social sobre o lucro (DRE), como informado: negativo quando e despesa. */
    IR_CSLL,
    /** Ativo circulante (BPA 1.01). So no layout comercial. */
    ATIVO_CIRCULANTE,
    /** Passivo circulante (BPP 2.01). So no layout comercial. */
    PASSIVO_CIRCULANTE,
    /** LPA basico informado pela companhia, por classe. So conferencia (ver ShareScale). */
    LPA_ON,
    LPA_PN;

    public boolean perShare() {
        return this == LPA_ON || this == LPA_PN;
    }
}
