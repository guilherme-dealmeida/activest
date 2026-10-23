package com.activest.domain.marketindex;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Acumulado de um indice entre duas datas (docs/planos/PLANO_INDICES_MERCADO.md, 3.2). Nada disso e gravado
 * (D6): a serie guarda o valor publicado e o acumulado sai daqui.
 *
 * <p><b>Taxa</b> ({@link IndexUnit#isCompounding()}: CDI, Selic, IPCA, IGP-M, INPC) — produto de
 * {@code 1 + taxa/100}, periodo a periodo. Duas variantes, as formas de remuneracao da renda fixa:</p>
 * <ul>
 *   <li><b>percentual do indice</b> (pos-fixado, "110% do CDI"): cada periodo rende
 *   {@code taxa × percentual/100};</li>
 *   <li><b>spread</b> (hibrido, "IPCA + 6% a.a."): cada periodo rende tambem
 *   {@code (1 + spread/100)^(1/n)}, com {@code n} = 252 no diario e 12 no mensal (meses inteiros).</li>
 * </ul>
 *
 * <p><b>Nivel</b> ({@link IndexUnit#isLevel()}: Ibovespa, IFIX, dolar) — ultimo valor ÷ valor base.</p>
 *
 * <p>TR e meta Selic nao acumulam (ver {@link IndexUnit}). Pro rata de mes incompleto, dias uteis futuros e
 * curva do titulo ficam para o escopo da valorizacao da renda fixa (D9).</p>
 */
public final class MarketIndexCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Casas do fator devolvido. */
    public static final int FACTOR_SCALE = 10;

    private MarketIndexCalculator() {
    }

    /**
     * @param firstDate primeiro ponto usado (na taxa) ou data da base (no nivel)
     * @param lastDate  ultimo ponto usado
     * @param periods   quantos periodos entraram (taxa) ou 1 (nivel)
     * @param factor    fator acumulado: 1,1088 = +10,88%
     */
    public record Accumulated(LocalDate firstDate, LocalDate lastDate, int periods, BigDecimal factor) {

        /** Variacao em %, 6 casas: (fator − 1) × 100. */
        public BigDecimal percent() {
            return factor.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(6, RoundingMode.HALF_EVEN);
        }
    }

    /**
     * Acumula uma serie de taxas, na ordem recebida.
     *
     * @param percentOfIndex percentual do indice (110 = 110%); nulo = 100%
     * @param spreadPerYear  spread anual em % (6 = 6% a.a.); nulo = sem spread
     * @throws IllegalArgumentException unidade que nao acumula ou serie vazia
     */
    public static Accumulated compound(IndexUnit unit, List<IndexPoint> rates, BigDecimal percentOfIndex,
                                       BigDecimal spreadPerYear) {
        if (!unit.isCompounding()) {
            throw new IllegalArgumentException("Indice em " + unit + " nao acumula por taxa");
        }
        if (rates.isEmpty()) {
            throw new IllegalArgumentException("Sem valores no periodo");
        }
        BigDecimal share = percentOfIndex == null ? BigDecimal.ONE : percentOfIndex.divide(HUNDRED, MC);
        BigDecimal spread = BigDecimal.ONE;
        if (spreadPerYear != null && spreadPerYear.signum() != 0) {
            double perPeriod = Math.pow(1 + spreadPerYear.doubleValue() / 100, 1.0 / unit.periodsPerYear());
            spread = new BigDecimal(perPeriod, MC);
        }
        BigDecimal factor = BigDecimal.ONE;
        for (IndexPoint p : rates) {
            BigDecimal period = BigDecimal.ONE.add(p.value().divide(HUNDRED, MC).multiply(share, MC), MC);
            factor = factor.multiply(period, MC).multiply(spread, MC);
        }
        return new Accumulated(rates.get(0).date(), rates.get(rates.size() - 1).date(), rates.size(),
                factor.setScale(FACTOR_SCALE, RoundingMode.HALF_EVEN));
    }

    /** Variacao de um indice de nivel: {@code end ÷ base}. */
    public static Accumulated level(IndexUnit unit, IndexPoint base, IndexPoint end) {
        if (!unit.isLevel()) {
            throw new IllegalArgumentException("Indice em " + unit + " nao e de nivel");
        }
        if (base.value().signum() == 0) {
            throw new IllegalArgumentException("Valor base zero em " + base.date());
        }
        BigDecimal factor = end.value().divide(base.value(), MC);
        return new Accumulated(base.date(), end.date(), 1, factor.setScale(FACTOR_SCALE, RoundingMode.HALF_EVEN));
    }
}
