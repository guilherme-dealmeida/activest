package com.primevest.domain.dividend;

/**
 * De onde veio a data de pagamento de um evento, quando nao veio da propria fonte dele (V24,
 * refeita na V38). Nulo = da fonte do evento.
 *
 * <p>A fonte do evento que traga a data depois substitui qualquer uma destas
 * (CashEventWriter.save).</p>
 */
public enum PaymentDateSource {
    /**
     * "Relatorio Proventos" da CVM (IPE, 2022 em diante): completa a data de pagamento do
     * historico longo da B3, que nao a traz (PLANO_CARGA_INICIAL.md, 5.2 — P3).
     */
    CVM,
    /**
     * Levantamento das datas de pagamento 2010–2026 ({@code data/levantamento/datas-pagamento.json},
     * PLANO_CARGA_INICIAL.md secao 11 — D14): completa o que a B3, o FNET e a CVM nao trazem.
     */
    LEVANTAMENTO
}
