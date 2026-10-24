package com.activest.domain.marketindex;

import com.activest.domain.pricehistory.PriceSeries;
import com.activest.domain.pricehistory.PriceSeries.Interval;
import com.activest.domain.pricehistory.PriceSeries.Range;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Regras puras do grafico de um indice na tela Indices (docs/planos/PLANO_INDICES_MERCADO.md, adendo de 05/10/2026):
 * janela de cada atalho, valor exibido e resumo do periodo. Sem banco — testavel sozinha.
 *
 * <ul>
 *   <li><b>Janela</b> — a mesma da cotacao ({@link PriceSeries#start}) nos indices diarios; nos mensais, meses
 *   inteiros contados do ultimo publicado (1A = os ultimos 12 meses; 1M = o ultimo mes; 1D e 7D nao existem no
 *   mensal — a tela nao mostra e a API recusa — e aqui caem no ultimo mes). No CDI/Selic o 1D
 *   e so o ultimo dia (a taxa de um dia ja e o "dia").</li>
 *   <li><b>Valor exibido</b> — taxa diaria (CDI, Selic) anualizada em 252 dias uteis: {@code ((1 + r/100)^252 − 1) × 100},
 *   como o mercado fala ("CDI a 14,90% a.a."); os demais como a fonte publica.</li>
 *   <li><b>Visao anual dos mensais</b> (IPCA, IGP-M, INPC) — cada mes mostra o acumulado dos 12 meses terminados
 *   nele (o "IPCA em 12 meses", em % a.a.), {@link #rolling12}; mes sem 12 meses de historia fica de fora.</li>
 *   <li><b>Resumo</b> — indice de taxa (CDI, Selic, IPCA, IGP-M, INPC): acumulado no periodo, com todas as taxas da
 *   janela (sempre a serie diaria, nunca a reduzida do grafico); nivel (Ibovespa, IFIX, dolar): variacao do primeiro
 *   ao ultimo ponto; TR e meta Selic: so ultimo, minimo e maximo.</li>
 * </ul>
 */
public final class IndexChart {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private IndexChart() {
    }

    /** Janela {@code [start, end]} do atalho, dentro da serie. */
    public record Window(LocalDate start, LocalDate end) {
    }

    public static Window window(IndexPeriodicity periodicity, IndexUnit unit, Range range,
                                NavigableMap<LocalDate, BigDecimal> series) {
        LocalDate last = series.lastKey();
        LocalDate first = series.firstKey();
        LocalDate start;
        if (periodicity == IndexPeriodicity.MENSAL) {
            start = switch (range) {
                case D1, D7, M1 -> last;
                case M6 -> last.minusMonths(5);
                case YTD -> last.withDayOfYear(1);
                case Y1 -> last.minusMonths(11);
                case Y5 -> last.minusMonths(59);
                case MAX -> first;
            };
        } else if (range == Range.D1 && unit.isCompounding()) {
            start = last;
        } else {
            start = PriceSeries.start(range, series);
        }
        return new Window(start.isBefore(first) ? first : start, last);
    }

    /** Densidade dos pontos do grafico: mensal sempre um ponto por mes; diario como a cotacao. */
    public static Interval interval(IndexPeriodicity periodicity, Range range, LocalDate start, LocalDate end) {
        if (periodicity == IndexPeriodicity.MENSAL || range == Range.D1 || range == Range.D7) {
            return Interval.DAY;
        }
        return PriceSeries.interval(start, end);
    }

    /** Valor exibido: taxa diaria anualizada (252); o resto como publicado. */
    public static BigDecimal display(IndexUnit unit, BigDecimal value) {
        if (unit == IndexUnit.PCT_DIA) {
            double annual = (Math.pow(1 + value.doubleValue() / 100, 252) - 1) * 100;
            return BigDecimal.valueOf(annual).setScale(4, RoundingMode.HALF_UP);
        }
        return value;
    }

    /** Como um indice mensal e mostrado: a variacao de cada mes ou o acumulado em 12 meses (a.a.). */
    public enum View { MENSAL, ANUAL }

    /**
     * Acumulado em 12 meses de uma serie mensal (% como publicada): para cada mes com os 11 anteriores presentes,
     * {@code (prod(1 + r/100) − 1) × 100} dos 12, 4 casas. Base da visao anual.
     */
    public static NavigableMap<LocalDate, BigDecimal> rolling12(NavigableMap<LocalDate, BigDecimal> monthly) {
        NavigableMap<LocalDate, BigDecimal> out = new TreeMap<>();
        for (LocalDate month : monthly.keySet()) {
            LocalDate start = month.minusMonths(11);
            NavigableMap<LocalDate, BigDecimal> twelve = monthly.subMap(start, true, month, true);
            if (twelve.size() != 12) {
                continue;
            }
            BigDecimal factor = BigDecimal.ONE;
            for (BigDecimal r : twelve.values()) {
                factor = factor.multiply(BigDecimal.ONE.add(r.divide(HUNDRED, MC)), MC);
            }
            out.put(month, factor.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP));
        }
        return out;
    }

    /** Unidade do valor exibido, para a tela. */
    public static String displayUnit(IndexUnit unit) {
        return switch (unit) {
            case PCT_DIA, PCT_ANO -> "% a.a.";
            case PCT_MES -> "% no mês";
            case PCT_PERIODO -> "% no período";
            case PONTOS -> "pontos";
            case BRL -> "R$";
        };
    }

    /** Resumo do periodo sobre a janela crua (valores como publicados). */
    public record Summary(BigDecimal last, BigDecimal min, BigDecimal max, BigDecimal change, BigDecimal changePct,
                          BigDecimal accumulatedPct) {
    }

    public static Summary summary(IndexUnit unit, NavigableMap<LocalDate, BigDecimal> window) {
        if (window.isEmpty()) {
            return new Summary(null, null, null, null, null, null);
        }
        BigDecimal min = null;
        BigDecimal max = null;
        for (BigDecimal raw : window.values()) {
            BigDecimal v = display(unit, raw);
            min = min == null || v.compareTo(min) < 0 ? v : min;
            max = max == null || v.compareTo(max) > 0 ? v : max;
        }
        BigDecimal last = display(unit, window.lastEntry().getValue());
        BigDecimal change = null;
        BigDecimal changePct = null;
        BigDecimal accumulated = null;
        if (unit.isLevel()) {
            BigDecimal a = window.firstEntry().getValue();
            BigDecimal b = window.lastEntry().getValue();
            change = b.subtract(a).setScale(4, RoundingMode.HALF_UP);
            changePct = a.signum() == 0 ? null
                    : b.divide(a, MC).subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
        } else if (unit.isCompounding()) {
            List<IndexPoint> rates = new ArrayList<>();
            window.forEach((d, v) -> rates.add(new IndexPoint(d, v)));
            accumulated = MarketIndexCalculator.compound(unit, rates, null, null).percent()
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return new Summary(last, min, max, change, changePct, accumulated);
    }

    /** Pontos do grafico (valor exibido) na densidade pedida. */
    public static List<Map.Entry<LocalDate, BigDecimal>> points(IndexUnit unit,
                                                                NavigableMap<LocalDate, BigDecimal> window,
                                                                Interval interval) {
        NavigableMap<LocalDate, BigDecimal> shown = new TreeMap<>();
        window.forEach((d, v) -> shown.put(d, display(unit, v)));
        return PriceSeries.points(shown, interval);
    }
}
