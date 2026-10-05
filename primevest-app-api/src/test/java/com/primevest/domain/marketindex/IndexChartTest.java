package com.primevest.domain.marketindex;

import com.primevest.domain.pricehistory.PriceSeries.Interval;
import com.primevest.domain.pricehistory.PriceSeries.Range;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras do grafico da tela Indices (docs/planos/PLANO_INDICES_MERCADO.md, adendo de 05/10/2026). */
class IndexChartTest {

    private static NavigableMap<LocalDate, BigDecimal> monthly(int months) {
        NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
        for (int i = 0; i < months; i++) {
            m.put(LocalDate.of(2024, 1, 1).plusMonths(i), new BigDecimal("0.40"));
        }
        return m;
    }

    @Test
    @DisplayName("Mensal: 1A = os ultimos 12 meses; 1D/7D/1M = so o ultimo; YTD = desde janeiro; ponto por mes")
    void janelaMensal() {
        NavigableMap<LocalDate, BigDecimal> ipca = monthly(32); // 01/2024 a 08/2026
        LocalDate ago26 = LocalDate.of(2026, 8, 1);

        assertThat(IndexChart.window(IndexPeriodicity.MENSAL, IndexUnit.PCT_MES, Range.Y1, ipca).start())
                .isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(IndexChart.window(IndexPeriodicity.MENSAL, IndexUnit.PCT_MES, Range.M1, ipca).start()).isEqualTo(ago26);
        assertThat(IndexChart.window(IndexPeriodicity.MENSAL, IndexUnit.PCT_MES, Range.D7, ipca).start()).isEqualTo(ago26);
        assertThat(IndexChart.window(IndexPeriodicity.MENSAL, IndexUnit.PCT_MES, Range.YTD, ipca).start())
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(IndexChart.window(IndexPeriodicity.MENSAL, IndexUnit.PCT_MES, Range.MAX, ipca).start())
                .isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(IndexChart.interval(IndexPeriodicity.MENSAL, Range.MAX, LocalDate.of(2010, 1, 1), ago26))
                .isEqualTo(Interval.DAY);
    }

    @Test
    @DisplayName("Diario: CDI no 1D e so o ultimo dia; Ibovespa no 1D vai do pregao anterior ao ultimo")
    void janelaDiaria() {
        NavigableMap<LocalDate, BigDecimal> s = new TreeMap<>();
        s.put(LocalDate.of(2026, 10, 1), BigDecimal.ONE);
        s.put(LocalDate.of(2026, 10, 2), BigDecimal.ONE);

        assertThat(IndexChart.window(IndexPeriodicity.DIARIA, IndexUnit.PCT_DIA, Range.D1, s).start())
                .isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(IndexChart.window(IndexPeriodicity.DIARIA, IndexUnit.PONTOS, Range.D1, s).start())
                .isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    @DisplayName("CDI exibido anualizado em 252 dias uteis: 0,055131% a.d. = 14,90% a.a.")
    void anualizado() {
        assertThat(IndexChart.display(IndexUnit.PCT_DIA, new BigDecimal("0.055131")).setScale(2, java.math.RoundingMode.HALF_UP))
                .isEqualByComparingTo("14.90");
        assertThat(IndexChart.display(IndexUnit.PCT_MES, new BigDecimal("0.42"))).isEqualByComparingTo("0.42");
        assertThat(IndexChart.displayUnit(IndexUnit.PCT_DIA)).isEqualTo("% a.a.");
    }

    @Test
    @DisplayName("Visao anual dos mensais: acumulado em 12 meses de cada mes; sem 12 meses de historia, fica de fora")
    void dozeMeses() {
        NavigableMap<LocalDate, BigDecimal> ipca = monthly(24); // 01/2024 a 12/2025
        NavigableMap<LocalDate, BigDecimal> twelve = IndexChart.rolling12(ipca);

        assertThat(twelve).hasSize(13);
        assertThat(twelve.firstKey()).isEqualTo(LocalDate.of(2024, 12, 1));
        assertThat(twelve.get(LocalDate.of(2025, 6, 1))).isEqualByComparingTo("4.9070"); // 1,004^12 − 1
    }

    @Test
    @DisplayName("Resumo: taxa acumula (IPCA 12 x 0,40%); nivel varia do primeiro ao ultimo; TR so ultimo/min/max")
    void resumo() {
        IndexChart.Summary ipca = IndexChart.summary(IndexUnit.PCT_MES, monthly(12));
        assertThat(ipca.accumulatedPct()).isEqualByComparingTo("4.9070"); // 1,004^12 − 1
        assertThat(ipca.change()).isNull();

        NavigableMap<LocalDate, BigDecimal> ibov = new TreeMap<>();
        ibov.put(LocalDate.of(2024, 1, 2), new BigDecimal("132696.63"));
        ibov.put(LocalDate.of(2024, 6, 3), new BigDecimal("140000"));
        ibov.put(LocalDate.of(2024, 12, 30), new BigDecimal("120283.40"));
        IndexChart.Summary b = IndexChart.summary(IndexUnit.PONTOS, ibov);
        assertThat(b.changePct()).isEqualByComparingTo("-9.35");
        assertThat(b.change()).isEqualByComparingTo("-12413.23");
        assertThat(b.max()).isEqualByComparingTo("140000");
        assertThat(b.accumulatedPct()).isNull();

        NavigableMap<LocalDate, BigDecimal> tr = new TreeMap<>();
        tr.put(LocalDate.of(2024, 5, 1), new BigDecimal("0.0870"));
        tr.put(LocalDate.of(2024, 5, 2), new BigDecimal("0.0512"));
        IndexChart.Summary t = IndexChart.summary(IndexUnit.PCT_PERIODO, tr);
        assertThat(t.last()).isEqualByComparingTo("0.0512");
        assertThat(t.min()).isEqualByComparingTo("0.0512");
        assertThat(t.accumulatedPct()).isNull();
        assertThat(t.changePct()).isNull();
    }
}
