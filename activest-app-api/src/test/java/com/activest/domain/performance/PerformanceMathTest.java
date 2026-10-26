package com.activest.domain.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Casos de conferencia do docs/planos/PLANO_RENTABILIDADE.md, secao 9 — o motor sem banco: linha do tempo montada a
 * mao (tres pregoes, preco 10 -> 11 -> 12,10), TWR, benchmarks, XIRR e risco.
 */
class PerformanceMathTest {

    private static final List<LocalDate> D = List.of(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14),
            LocalDate.of(2026, 10, 15));

    /** Uma posicao: valor ao fim de cada dia, aportes, retiradas, proventos e situacao do preco. */
    private static PortfolioTimeline.PositionSeries pos(String ticker, double[] value, double[] buy, double[] sell,
                                                        double[] dist, PriceStatus... status) {
        int n = value.length;
        boolean[] held = new boolean[n];
        PriceStatus[] st = new PriceStatus[n];
        for (int i = 0; i < n; i++) {
            held[i] = value[i] > 0;
            st[i] = status.length > i ? status[i] : PriceStatus.OK;
        }
        double[] distNet = dist.clone();
        return new PortfolioTimeline.PositionSeries(UUID.randomUUID(), 1L, ticker, ticker, "ACAO", "Ações", 1L,
                "Bancos", null, null, false, false, value, value.clone(), buy, sell, sell.clone(), dist, distNet,
                held, st, new LocalDate[n], List.of());
    }

    private static ScopeSeries scope(PortfolioTimeline.PositionSeries... p) {
        return ScopeSeries.of(new PortfolioTimeline(D, List.of(p), D.get(2)), x -> true, PerformanceBasis.NET);
    }

    private static final double[] Z = {0, 0, 0};

    @Test
    @DisplayName("C1 — sem movimento: TWR = variacao do valor (10 -> 12,10 = 21%)")
    void c1() {
        ScopeSeries s = scope(pos("A", new double[]{100, 110, 121}, new double[]{100, 0, 0}, Z, Z));
        assertThat(ReturnMath.chain(ReturnMath.daily(s), 0, 2)).isCloseTo(0.21, within(1e-12));
    }

    @Test
    @DisplayName("C2 — aporte no meio ao fechamento da vespera: TWR igual ao de C1 (aporte nao e rendimento)")
    void c2() {
        // 10 cotas a 10; no dia 2 mais 10 cotas a 10 (o preco de abertura), fechando a 11
        ScopeSeries s = scope(pos("A", new double[]{100, 220, 242}, new double[]{100, 100, 0}, Z, Z));
        assertThat(ReturnMath.chain(ReturnMath.daily(s), 0, 2)).isCloseTo(0.21, within(1e-12));
    }

    @Test
    @DisplayName("C3 — retirada: venda pelo fechamento do dia nao muda o TWR")
    void c3() {
        // 20 cotas; no dia 2 vende 10 a 11 (=110), ficam 10 valendo 110
        ScopeSeries s = scope(pos("A", new double[]{200, 110, 121}, new double[]{200, 0, 0}, new double[]{0, 110, 0}, Z));
        assertThat(ReturnMath.chain(ReturnMath.daily(s), 0, 2)).isCloseTo(0.21, within(1e-12));
    }

    @Test
    @DisplayName("C4 — provento no pregao ex: a queda da cotacao e compensada (sem queda falsa)")
    void c4() {
        // preco parado em 10; provento de 1 por cota, a cotacao cai para 9 no pregao ex
        ScopeSeries s = scope(pos("A", new double[]{100, 90, 90}, new double[]{100, 0, 0}, Z, new double[]{0, 10, 0}));
        double[] r = ReturnMath.daily(s);
        assertThat(r[1]).isCloseTo(0, within(1e-12));
        assertThat(ReturnMath.chain(r, 0, 2)).isCloseTo(0, within(1e-12));
    }

    @Test
    @DisplayName("C6 — sem preco num dia: o periodo fica indisponivel; filtrando o ativo, calcula")
    void c6() {
        PortfolioTimeline.PositionSeries a = pos("A", new double[]{100, 110, 121}, new double[]{100, 0, 0}, Z, Z);
        PortfolioTimeline.PositionSeries btc = pos("BTC", new double[]{50, 50, 55}, new double[]{50, 0, 0}, Z, Z,
                PriceStatus.OK, PriceStatus.MISSING, PriceStatus.OK);
        PortfolioTimeline t = new PortfolioTimeline(D, List.of(a, btc), D.get(2));
        ScopeSeries all = ScopeSeries.of(t, x -> true, PerformanceBasis.NET);
        assertThat(all.unavailable[1]).isTrue();
        assertThat(all.unavailable[2]).isTrue(); // a vespera sem preco tambem invalida o dia seguinte
        assertThat(ReturnMath.chain(ReturnMath.daily(all), 0, 2)).isNull();
        assertThat(all.missingTickers(0, 2)).containsExactly("BTC");
        ScopeSeries withoutCrypto = ScopeSeries.of(t, p -> !p.ticker().equals("BTC"), PerformanceBasis.NET);
        assertThat(ReturnMath.chain(ReturnMath.daily(withoutCrypto), 0, 2)).isCloseTo(0.21, within(1e-12));
    }

    @Test
    @DisplayName("C6b — preco carregado (revisao 25/10/2026): o dia continua disponivel, marcado, e a lacuna aparece")
    void c6carried() {
        PortfolioTimeline.PositionSeries a = pos("A", new double[]{100, 110, 121}, new double[]{100, 0, 0}, Z, Z);
        // o titulo fica com o ultimo preco (50) carregado no dia 2
        PortfolioTimeline.PositionSeries t1 = pos("TD", new double[]{50, 50, 55}, new double[]{50, 0, 0}, Z, Z,
                PriceStatus.OK, PriceStatus.CARRIED, PriceStatus.OK);
        ScopeSeries all = ScopeSeries.of(new PortfolioTimeline(D, List.of(a, t1), D.get(2)), x -> true,
                PerformanceBasis.NET);
        assertThat(all.unavailable).containsOnly(false);
        assertThat(all.stale[1]).isTrue();
        // (100 + 50) -> (110 + 50) -> (121 + 55)
        assertThat(ReturnMath.chain(ReturnMath.daily(all), 0, 2)).isCloseTo(176.0 / 150 - 1, within(1e-12));
        assertThat(all.missingTickers(0, 2)).isEmpty();
        assertThat(all.gaps(0, 2)).extracting(ScopeSeries.Gap::status).containsExactly(PriceStatus.CARRIED);
    }

    @Test
    @DisplayName("C9 / D5 — % do CDI: negativo com carteira negativa; sem CDI positivo, nulo")
    void percentOfCdi() {
        assertThat(ReturnMath.percentOfCdi(-0.05, 0.10)).isCloseTo(-50, within(1e-9));
        assertThat(ReturnMath.percentOfCdi(0.12, 0.10)).isCloseTo(120, within(1e-9));
        assertThat(ReturnMath.percentOfCdi(0.12, 0.0)).isNull();
        assertThat(ReturnMath.percentOfCdi(0.12, -0.01)).isNull();
    }

    @Test
    @DisplayName("R7 — real: (1 + nominal) / (1 + IPCA) - 1")
    void real() {
        assertThat(ReturnMath.real(0.10, 0.04)).isCloseTo(1.10 / 1.04 - 1, within(1e-12));
    }

    @Test
    @DisplayName("R9 — CDI por dia util; depois da ultima taxa publicada o fator e 1")
    void cdi() {
        NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>();
        rates.put(D.get(0), new BigDecimal("0.05"));
        rates.put(D.get(1), new BigDecimal("0.05"));
        BenchmarkSeries.Daily b = BenchmarkSeries.dailyRate(D, rates);
        assertThat(b.r()[0]).isCloseTo(0.0005, within(1e-12));
        assertThat(b.r()[1]).isCloseTo(0.0005, within(1e-12));
        assertThat(b.r()[2]).isCloseTo(0, within(1e-12));
        assertThat(b.until()).isEqualTo(D.get(1));
    }

    @Test
    @DisplayName("R9 — IPCA: o mes inteiro soma a taxa do mes; mes nao publicado fica sem fator; IPCA + x compoe")
    void ipca() {
        List<LocalDate> axis = new ArrayList<>();
        for (LocalDate d = LocalDate.of(2026, 8, 31); d.isBefore(LocalDate.of(2026, 10, 3)); d = d.plusDays(1)) {
            if (com.activest.domain.calendar.BusinessCalendar.isBusinessDay(d)) {
                axis.add(d);
            }
        }
        NavigableMap<LocalDate, BigDecimal> months = new TreeMap<>();
        months.put(LocalDate.of(2026, 8, 1), new BigDecimal("0.30"));
        months.put(LocalDate.of(2026, 9, 1), new BigDecimal("1.00"));
        BenchmarkSeries.Daily b = BenchmarkSeries.monthlyRate(axis, months, 0);
        int first = 1; // 01/09
        int last = axis.indexOf(LocalDate.of(2026, 9, 30));
        assertThat(ReturnMath.chain(b.r(), first, last)).isCloseTo(0.01, within(1e-12));
        assertThat(Double.isNaN(b.r()[last + 1])).isTrue(); // outubro nao publicado
        assertThat(b.until()).isEqualTo(LocalDate.of(2026, 9, 30));
        BenchmarkSeries.Daily more = BenchmarkSeries.monthlyRate(axis, months, 6);
        int du = last - first + 1;
        assertThat(ReturnMath.chain(more.r(), first, last))
                .isCloseTo(1.01 * Math.pow(1.06, du / 252.0) - 1, within(1e-12));
    }

    @Test
    @DisplayName("R9 — nivel: razao entre fechamentos; preco de hoje = o atual")
    void level() {
        NavigableMap<LocalDate, Double> lv = new TreeMap<>();
        lv.put(D.get(0).minusDays(1), 100.0);
        lv.put(D.get(0), 110.0);
        lv.put(D.get(1), 121.0);
        BenchmarkSeries.Daily b = BenchmarkSeries.level(D, lv, D.get(2), 133.1);
        assertThat(ReturnMath.chain(b.r(), 0, 2)).isCloseTo(0.331, within(1e-12));
    }

    @Test
    @DisplayName("R6 — XIRR: aplica 100, resgata 110 um ano depois = 10% a.a.")
    void xirr() {
        Double x = Xirr.of(List.of(new Xirr.CashFlow(LocalDate.of(2025, 1, 1), -100),
                new Xirr.CashFlow(LocalDate.of(2026, 1, 1), 110)));
        assertThat(x).isCloseTo(0.10, within(1e-6));
        assertThat(Xirr.of(List.of(new Xirr.CashFlow(LocalDate.of(2025, 1, 1), 100)))).isNull();
    }

    @Test
    @DisplayName("R14 — drawdown e volatilidade")
    void risk() {
        double[] r = {0.10, -0.20, 0.05, 0.0};
        double[] cdi = {0.0004, 0.0004, 0.0004, 0.0004};
        List<LocalDate> dates = List.of(D.get(0), D.get(1), D.get(2), D.get(2).plusDays(1));
        RiskMetrics.Result m = RiskMetrics.of(r, cdi, dates);
        assertThat(m.maxDrawdown()).isCloseTo(-0.20, within(1e-12));
        assertThat(m.peak()).isEqualTo(D.get(0));
        assertThat(m.trough()).isEqualTo(D.get(1));
        assertThat(m.volatility()).isGreaterThan(0);
    }
}
