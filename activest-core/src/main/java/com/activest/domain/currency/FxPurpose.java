package com.activest.domain.currency;

/** Finalidade de uma operação de câmbio (V9): decide a regra de IOF. */
public enum FxPurpose {
    INVESTMENT(ForeignTaxEvent.REMITTANCE_INVESTMENT),
    OWN_ACCOUNT(ForeignTaxEvent.REMITTANCE_OWN_ACCOUNT),
    INFLOW(ForeignTaxEvent.INFLOW);

    private final ForeignTaxEvent iofEvent;

    FxPurpose(ForeignTaxEvent iofEvent) {
        this.iofEvent = iofEvent;
    }

    public ForeignTaxEvent iofEvent() {
        return iofEvent;
    }
}
