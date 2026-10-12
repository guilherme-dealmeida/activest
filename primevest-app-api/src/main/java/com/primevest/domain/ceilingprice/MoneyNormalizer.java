package com.primevest.domain.ceilingprice;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Normalização do lucro digitado (docs/planos/PLANO_PRECO_TETO.md, 4.3): {@code valor × fator(unidade) × câmbio},
 * em reais com 2 casas. Função pura.
 */
public final class MoneyNormalizer {

    private MoneyNormalizer() {
    }

    public enum Unit {
        UNIDADE(BigDecimal.ONE), MIL(new BigDecimal("1000")), MILHAO(new BigDecimal("1000000")),
        BILHAO(new BigDecimal("1000000000"));

        private final BigDecimal factor;

        Unit(BigDecimal factor) {
            this.factor = factor;
        }

        public BigDecimal factor() {
            return factor;
        }
    }

    /** Moedas aceitas (D8): real e dólar (PTAX). */
    public enum Currency { BRL, USD }

    /**
     * @param fxRate câmbio para reais; obrigatório e positivo em USD, ignorado (1) em BRL
     */
    public static BigDecimal toBrl(BigDecimal value, Unit unit, Currency currency, BigDecimal fxRate) {
        if (value == null || unit == null || currency == null) {
            throw new IllegalArgumentException("valor, unidade e moeda são obrigatórios");
        }
        BigDecimal rate = BigDecimal.ONE;
        if (currency == Currency.USD) {
            if (fxRate == null || fxRate.signum() <= 0) {
                throw new IllegalArgumentException("câmbio obrigatório e positivo para valor em dólar");
            }
            rate = fxRate;
        }
        return value.multiply(unit.factor()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
