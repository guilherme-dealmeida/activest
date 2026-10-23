package com.activest.domain.marketindex;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Acumulado dos indices (docs/planos/PLANO_INDICES_MERCADO.md, 3.2 e casos de conferencia da secao 6). */
class MarketIndexCalculatorTest {

    /** IPCA de 2024 como o SGS publica (serie 433). O IBGE divulgou 4,83% no ano. */
    private static final String[] IPCA_2024 = {"0.42", "0.83", "0.16", "0.38", "0.46", "0.21", "0.38", "-0.02",
            "0.44", "0.56", "0.39", "0.52"};

    private static List<IndexPoint> ipca2024() {
        List<IndexPoint> l = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            l.add(new IndexPoint(LocalDate.of(2024, m + 1, 1), new BigDecimal(IPCA_2024[m])));
        }
        return l;
    }

    @Test
    @DisplayName("IPCA acumulado de 2024 = 4,83%, o numero oficial do IBGE")
    void officialIpca2024() {
        MarketIndexCalculator.Accumulated a = MarketIndexCalculator.compound(IndexUnit.PCT_MES, ipca2024(), null, null);

        assertThat(a.periods()).isEqualTo(12);
        assertThat(a.firstDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(a.lastDate()).isEqualTo(LocalDate.of(2024, 12, 1));
        assertThat(a.percent().setScale(2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("4.83");
    }

    @Test
    @DisplayName("Percentual do indice: 110% de 1% ao dia por 2 dias = 1,011^2")
    void indexPercentage() {
        List<IndexPoint> r = List.of(new IndexPoint(LocalDate.of(2024, 1, 2), new BigDecimal("1")),
                new IndexPoint(LocalDate.of(2024, 1, 3), new BigDecimal("1")));

        MarketIndexCalculator.Accumulated a = MarketIndexCalculator.compound(IndexUnit.PCT_DIA, r,
                new BigDecimal("110"), null);

        assertThat(a.factor()).isEqualByComparingTo("1.022121");
    }

    @Test
    @DisplayName("Spread: taxa zero por 252 dias uteis com 6% a.a. rende exatamente 6%")
    void annualSpread() {
        List<IndexPoint> r = new ArrayList<>();
        LocalDate d = LocalDate.of(2024, 1, 1);
        for (int i = 0; i < 252; i++) {
            r.add(new IndexPoint(d.plusDays(i), BigDecimal.ZERO));
        }

        MarketIndexCalculator.Accumulated a = MarketIndexCalculator.compound(IndexUnit.PCT_DIA, r, null,
                new BigDecimal("6"));

        assertThat(a.percent().setScale(6, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("6.000000");
    }

    @Test
    @DisplayName("IPCA + 6% a.a. em 2024 (meses inteiros) = 1,0483 × 1,06")
    void ipcaPlusSpread() {
        MarketIndexCalculator.Accumulated a = MarketIndexCalculator.compound(IndexUnit.PCT_MES, ipca2024(), null,
                new BigDecimal("6"));

        BigDecimal ipca = MarketIndexCalculator.compound(IndexUnit.PCT_MES, ipca2024(), null, null).factor();
        assertThat(a.factor().doubleValue()).isCloseTo(ipca.doubleValue() * 1.06, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("Nivel: variacao do Ibovespa = fim ÷ base")
    void levelPoint() {
        MarketIndexCalculator.Accumulated a = MarketIndexCalculator.level(IndexUnit.PONTOS,
                new IndexPoint(LocalDate.of(2024, 1, 2), new BigDecimal("132696.63")),
                new IndexPoint(LocalDate.of(2024, 12, 30), new BigDecimal("120283.40")));

        assertThat(a.percent().setScale(2, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("-9.35");
    }

    @Test
    @DisplayName("TR e meta Selic nao acumulam; serie vazia e recusada")
    void rejections() {
        List<IndexPoint> um = List.of(new IndexPoint(LocalDate.of(2024, 1, 2), BigDecimal.ONE));
        assertThatThrownBy(() -> MarketIndexCalculator.compound(IndexUnit.PCT_PERIODO, um, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketIndexCalculator.compound(IndexUnit.PCT_ANO, um, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketIndexCalculator.compound(IndexUnit.PCT_DIA, List.of(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MarketIndexCalculator.level(IndexUnit.PCT_DIA, um.get(0), um.get(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
