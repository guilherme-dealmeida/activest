package com.primevest.domain.marketindex;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Um ponto de uma serie: data e valor como a fonte publica. Mensal: dia 1o do mes. */
public record IndexPoint(LocalDate date, BigDecimal value) {

    public static IndexPoint of(MarketIndexValue v) {
        return new IndexPoint(v.getReferenceDate(), v.getValue());
    }
}
