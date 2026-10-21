package com.primevest.domain.dividend;

/**
 * Fonte de um evento de provento do catalogo (V20). So integracao com as fontes de catalogo: a B3 (recente e
 * historico longo) e o FNET (docs/planos/PLANO_CARGA_INICIAL.md, regra C).
 */
public enum CashEventSource {
    /** Proventos recentes (~12 meses) do site da B3, com data de pagamento (acumulados em carga/data/b3/proventos-12m). */
    B3,
    /** Historico longo de empresa da B3, desde os anos 90, sem data de pagamento. */
    B3_HISTORICO,
    /** Aviso aos cotistas estruturado do Fundos.NET: fonte principal de FII (D15). */
    FNET
}
