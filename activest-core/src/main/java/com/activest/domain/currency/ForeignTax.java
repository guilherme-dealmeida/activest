package com.activest.domain.currency;

/** Tributo do exterior (V9, seção 5 do plano). */
public enum ForeignTax {
    /** IOF sobre a operação de câmbio (remessa, ingresso). */
    IOF_FX,
    /** IR brasileiro sobre aplicações no exterior (Lei 14.754/2023 desde 2024; regime anterior antes). */
    IR_FOREIGN,
    /** Retenção na fonte do país de origem do ativo (EUA: 30% sobre dividendos). */
    WITHHOLDING_FOREIGN
}
