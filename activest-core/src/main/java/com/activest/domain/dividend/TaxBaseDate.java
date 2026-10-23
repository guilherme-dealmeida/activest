package com.activest.domain.dividend;

/**
 * Qual data do provento cai na vigencia de uma {@link WithholdingTaxRule}.
 */
public enum TaxBaseDate {
    /**
     * Data do credito ao acionista: a aprovacao ({@code approved_on}); sem ela,
     * a data-com; sem as duas, o pagamento. E o fato gerador do JCP — "na data
     * do pagamento ou do credito", o que vier primeiro, e o credito vem antes.
     * Conferido no extrato da B3: JCP aprovado em 11/12/2025 e pago em
     * 20/02/2026 reteve 15%.
     */
    CREDIT,
    /** Data de pagamento; sem ela, a data-com. */
    PAYMENT
}
