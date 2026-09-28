package com.primevest.domain.dividend;


/**
 * De onde veio a data de pagamento de um evento, quando nao veio da propria
 * fonte dele (V24). Nulo = oficial, da fonte do evento.
 *
 * <p>Uma fonte oficial que traga a data depois substitui qualquer uma destas
 * (CashEventWriter.save).</p>
 */
public enum PaymentDateSource {
    /** Do extrato de proventos da Area do Investidor da B3. */
    EXTRATO_B3,
    /** Deduzida do padrao de pagamento da empresa; ainda sem confirmacao. */
    DEDUZIDA
}
