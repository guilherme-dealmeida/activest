package com.activest.domain.ceilingprice;

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

    /**
     * A moeda vem do cadastro de moedas (docs/planos/PLANO_CADASTRO_MOEDAS.md, D9): quem chama diz se é a moeda base.
     *
     * @param base   valor na moeda base (reais): câmbio ignorado (1)
     * @param fxRate câmbio para reais; obrigatório e positivo fora da moeda base
     */
    public static BigDecimal toBrl(BigDecimal value, Unit unit, boolean base, BigDecimal fxRate) {
        if (value == null || unit == null) {
            throw new IllegalArgumentException("valor e unidade são obrigatórios");
        }
        BigDecimal rate = BigDecimal.ONE;
        if (!base) {
            if (fxRate == null || fxRate.signum() <= 0) {
                throw new IllegalArgumentException("câmbio obrigatório e positivo para valor em outra moeda");
            }
            rate = fxRate;
        }
        return value.multiply(unit.factor()).multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
