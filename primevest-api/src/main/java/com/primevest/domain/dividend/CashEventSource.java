package com.primevest.domain.dividend;

/** Fonte de um evento de provento do catalogo (V20). */
public enum CashEventSource {
    /** Proventos recentes (~12 meses) do site da B3, com data de pagamento. */
    B3,
    /** Historico longo de empresa da B3, desde os anos 90, sem data de pagamento. */
    B3_HISTORICO,
    /** Aviso aos cotistas estruturado do Fundos.NET (FII alem de 12 meses). */
    FNET,
    /**
     * Extrato de proventos da Area do Investidor da B3 (V24). Traz o pagamento;
     * a data-com e deduzida do padrao do fundo e conferida contra as cotas.
     */
    EXTRATO_B3
}
