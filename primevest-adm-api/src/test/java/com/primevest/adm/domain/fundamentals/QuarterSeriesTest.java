package com.primevest.adm.domain.fundamentals;

import com.primevest.adm.domain.fundamentals.QuarterSeries.Doc;
import com.primevest.adm.domain.fundamentals.QuarterSeries.Flow;
import com.primevest.adm.domain.fundamentals.QuarterSeries.Quarter;
import com.primevest.adm.domain.fundamentals.QuarterSeries.Shares;
import com.primevest.adm.domain.fundamentals.QuarterSeries.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.primevest.adm.domain.fundamentals.FinancialMetric.*;
import static org.assertj.core.api.Assertions.assertThat;

class QuarterSeriesTest {

    private static long ids = 1;

    /** Documento: {metrica, inicio (ou null), valor}. */
    private static Doc doc(String type, String ref, Shares shares, Object[]... values) {
        LocalDate end = LocalDate.parse(ref);
        List<Value> list = new ArrayList<>();
        for (Object[] v : values) {
            list.add(new Value((FinancialMetric) v[0], v[1] == null ? null : LocalDate.parse((String) v[1]), end,
                    new BigDecimal(v[2].toString())));
        }
        return new Doc(ids++, type, end, list, shares);
    }

    private static Object[] v(FinancialMetric m, String start, long value) {
        return new Object[]{m, start, value};
    }

    private static Map<LocalDate, Quarter> byDate(List<Doc> docs) {
        return QuarterSeries.compute(docs).stream().collect(Collectors.toMap(Quarter::periodEnd, Function.identity()));
    }

    private static BigDecimal q(Map<LocalDate, Quarter> m, String date, Flow f) {
        return m.get(LocalDate.parse(date)).quarter().get(f);
    }

    private static BigDecimal ttm(Map<LocalDate, Quarter> m, String date, Flow f) {
        return m.get(LocalDate.parse(date)).ttm().get(f);
    }

    /** Ano civil: ITR com coluna de 3 meses na DRE, DFC e DVA so acumuladas. */
    private static List<Doc> calendarYear() {
        return new ArrayList<>(List.of(
                doc("DFP", "2024-12-31", Shares.NONE, v(RECEITA, "2024-01-01", 400), v(FCO, "2024-01-01", 100)),
                doc("ITR", "2025-03-31", Shares.NONE, v(RECEITA, "2025-01-01", 110), v(FCO, "2025-01-01", 30),
                        v(DIVIDENDOS_PAGOS, "2025-01-01", 25),
                        v(JCP_DECLARADO, "2025-01-01", 5), v(DIVIDENDOS_DECLARADOS, "2025-01-01", 7)),
                doc("ITR", "2025-06-30", Shares.NONE, v(RECEITA, "2025-01-01", 230), v(RECEITA, "2025-04-01", 120),
                        v(FCO, "2025-01-01", 70), v(DIVIDENDOS_PAGOS, "2025-01-01", 60),
                        v(JCP_DECLARADO, "2025-01-01", 10), v(DIVIDENDOS_DECLARADOS, "2025-01-01", 7)),
                doc("ITR", "2025-09-30", Shares.NONE, v(RECEITA, "2025-01-01", 360), v(RECEITA, "2025-07-01", 130),
                        v(FCO, "2025-01-01", 95)),
                doc("DFP", "2025-12-31", Shares.NONE, v(RECEITA, "2025-01-01", 500), v(FCO, "2025-01-01", 140)),
                doc("ITR", "2026-03-31", Shares.NONE, v(RECEITA, "2026-01-01", 150), v(FCO, "2026-01-01", 20))));
    }

    @Test
    @DisplayName("Ano civil: 3 meses do ITR, 4T = DFP - 9 meses, DFC por diferenca, 12 meses anual ou soma de 4")
    void fullCalendarYear() {
        Map<LocalDate, Quarter> m = byDate(calendarYear());

        assertThat(q(m, "2025-03-31", Flow.REVENUE)).isEqualByComparingTo("110");
        assertThat(q(m, "2025-06-30", Flow.REVENUE)).isEqualByComparingTo("120");
        assertThat(q(m, "2025-09-30", Flow.REVENUE)).isEqualByComparingTo("130");
        assertThat(q(m, "2025-12-31", Flow.REVENUE)).isEqualByComparingTo("140"); // 500 - 360
        assertThat(q(m, "2026-03-31", Flow.REVENUE)).isEqualByComparingTo("150");

        assertThat(q(m, "2025-06-30", Flow.FCO)).isEqualByComparingTo("40");      // 70 - 30
        assertThat(q(m, "2025-12-31", Flow.FCO)).isEqualByComparingTo("45");      // 140 - 95

        // dividendos + JCP da DVA
        assertThat(q(m, "2025-03-31", Flow.DIVIDENDS_DECLARED)).isEqualByComparingTo("12");
        assertThat(q(m, "2025-06-30", Flow.DIVIDENDS_DECLARED)).isEqualByComparingTo("5");
        // dividendos + JCP pagos (DFC, so acumulado): trimestre por diferenca, como o FCO
        assertThat(q(m, "2025-03-31", Flow.DIVIDENDS_PAID)).isEqualByComparingTo("25");
        assertThat(q(m, "2025-06-30", Flow.DIVIDENDS_PAID)).isEqualByComparingTo("35");   // 60 - 25

        assertThat(ttm(m, "2025-12-31", Flow.REVENUE)).isEqualByComparingTo("500"); // anual do DFP
        assertThat(ttm(m, "2026-03-31", Flow.REVENUE)).isEqualByComparingTo("540"); // 120+130+140+150
        assertThat(ttm(m, "2026-03-31", Flow.FCO)).isEqualByComparingTo("130");     // 40+25+45+20
        // antes de 2025 nao ha 4 trimestres
        assertThat(ttm(m, "2025-09-30", Flow.REVENUE)).isNull();
        assertThat(m.get(LocalDate.parse("2025-09-30")).fiscalYearStart()).isEqualTo("2025-01-01");
    }

    @Test
    @DisplayName("Faltando um ITR: trimestre por diferenca e 12 meses ficam nulos, nao estima")
    void missingOneQuarter() {
        List<Doc> docs = calendarYear();
        docs.removeIf(d -> d.referenceDate().equals(LocalDate.parse("2025-06-30")));
        Map<LocalDate, Quarter> m = byDate(docs);

        assertThat(q(m, "2025-09-30", Flow.REVENUE)).isEqualByComparingTo("130"); // coluna de 3 meses
        assertThat(q(m, "2025-09-30", Flow.FCO)).isNull();                         // sem o 2T para subtrair
        assertThat(ttm(m, "2025-12-31", Flow.REVENUE)).isEqualByComparingTo("500"); // anual segue valendo
        assertThat(ttm(m, "2026-03-31", Flow.REVENUE)).isNull();
        assertThat(m).hasSize(5);
    }

    @Test
    @DisplayName("Exercicio de marco a fevereiro (Camil): 4T termina em fevereiro, anual pelo DFP de fevereiro")
    void fiscalYearOffCalendar() {
        Map<LocalDate, Quarter> m = byDate(List.of(
                doc("ITR", "2024-05-31", Shares.NONE, v(LUCRO_CONTROLADORA, "2024-03-01", 10)),
                doc("ITR", "2024-08-31", Shares.NONE, v(LUCRO_CONTROLADORA, "2024-03-01", 25),
                        v(LUCRO_CONTROLADORA, "2024-06-01", 15)),
                doc("ITR", "2024-11-30", Shares.NONE, v(LUCRO_CONTROLADORA, "2024-03-01", 45),
                        v(LUCRO_CONTROLADORA, "2024-09-01", 20)),
                doc("DFP", "2025-02-28", Shares.NONE, v(LUCRO_CONTROLADORA, "2024-03-01", 70)),
                doc("ITR", "2025-05-31", Shares.NONE, v(LUCRO_CONTROLADORA, "2025-03-01", 12))));

        assertThat(q(m, "2025-02-28", Flow.NET_INCOME_PARENT)).isEqualByComparingTo("25"); // 70 - 45
        assertThat(ttm(m, "2025-02-28", Flow.NET_INCOME_PARENT)).isEqualByComparingTo("70");
        assertThat(ttm(m, "2025-05-31", Flow.NET_INCOME_PARENT)).isEqualByComparingTo("72"); // 15+20+25+12
        assertThat(m.get(LocalDate.parse("2025-02-28")).fiscalYearStart()).isEqualTo("2024-03-01");
    }

    @Test
    @DisplayName("Posicao do proprio balanco, PL da controladora, acoes; DFP vale sobre ITR na mesma data")
    void positionAndShares() {
        Map<LocalDate, Quarter> m = byDate(List.of(
                doc("ITR", "2025-12-31", Shares.NONE, v(PL, null, 1)),
                doc("DFP", "2025-12-31", new Shares(1000L, 990L), v(PL, null, 500), v(PL_NAO_CONTROLADORES, null, 20),
                        v(ATIVO_TOTAL, null, 2000), v(CAIXA, null, 50), v(DIVIDA_BRUTA, null, 300)),
                doc("ITR", "2026-03-31", null, v(PL, null, 510))));

        Quarter ten = m.get(LocalDate.parse("2025-12-31"));
        assertThat(ten.equity()).isEqualByComparingTo("500");
        assertThat(ten.equityParent()).isEqualByComparingTo("480");
        assertThat(ten.totalAssets()).isEqualByComparingTo("2000");
        assertThat(ten.cash()).isEqualByComparingTo("50");
        assertThat(ten.grossDebt()).isEqualByComparingTo("300");
        assertThat(ten.sharesTotal()).isEqualTo(1000L);
        assertThat(ten.sharesOutstanding()).isEqualTo(990L);

        Quarter mar = m.get(LocalDate.parse("2026-03-31"));
        assertThat(mar.equityParent()).isEqualByComparingTo("510"); // sem nao controladores (individual)
        assertThat(mar.sharesOutstanding()).isNull();
        assertThat(mar.quarter()).isEmpty();
        assertThat(mar.fiscalYearStart()).isNull();
    }

    @Test
    @DisplayName("Trimestres consecutivos: 80 a 100 dias entre as datas")
    void distanceBetweenQuarters() {
        assertThat(QuarterSeries.isQuarterApart(LocalDate.parse("2024-11-30"), LocalDate.parse("2025-02-28"))).isTrue();
        assertThat(QuarterSeries.isQuarterApart(LocalDate.parse("2025-03-31"), LocalDate.parse("2025-06-30"))).isTrue();
        assertThat(QuarterSeries.isQuarterApart(LocalDate.parse("2025-03-31"), LocalDate.parse("2025-09-30"))).isFalse();
    }
}
