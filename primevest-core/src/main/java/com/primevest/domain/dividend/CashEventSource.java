package com.primevest.domain.dividend;

/**
 * Fonte de um evento de provento do catalogo (V20). So integracao: o extrato do investidor
 * ({@code EXTRATO_B3}) deixou de ser fonte do catalogo em 29/09/2026 (V38, PLANO_CARGA_INICIAL.md).
 */
public enum CashEventSource {
    /** Proventos recentes (~12 meses) do site da B3, com data de pagamento (acumulados em data/b3/proventos-12m). */
    B3,
    /** Historico longo de empresa da B3, desde os anos 90, sem data de pagamento. */
    B3_HISTORICO,
    /** Aviso aos cotistas estruturado do Fundos.NET: fonte principal de FII (D15). */
    FNET
}
