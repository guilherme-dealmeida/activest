package com.primevest.domain.performance;

import com.primevest.domain.calendar.BusinessCalendar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * O retorno de um benchmark em cada dia do eixo da carteira (docs/planos/PLANO_RENTABILIDADE.md, R9): o fator do dia
 * e o que o indice andou desde o dia anterior do eixo. Regra pura.
 *
 * <ul>
 *   <li><b>Taxa diaria</b> (CDI, Selic): {@code 1 + v/100} por dia util. Depois da ultima taxa publicada o fator e 1
 *   (como a curva da renda fixa); {@code until} diz ate onde ha dado.</li>
 *   <li><b>Taxa mensal</b> (IPCA): {@code (1 + I/100)^(1/DU_mes)} por dia util (D6 da valorizacao), vezes
 *   {@code (1 + x)^(1/252)} no IPCA + x%. Mes nao publicado: sem fator (NaN) — o benchmark para no ultimo mes
 *   publicado (L11).</li>
 *   <li><b>Nivel</b> (Ibovespa, IFIX, IVVB11, BTC): razao entre fechamentos; sem negocio, o ultimo ate
 *   {@link PortfolioTimeline#MAX_STALE_DAYS} pregoes; alem disso, NaN.</li>
 * </ul>
 */
public final class BenchmarkSeries {

    private BenchmarkSeries() {
    }

    /**
     * @param r     retorno de cada dia do eixo (NaN = sem dado)
     * @param until ultimo dia com dado efetivo (nulo se nenhum)
     */
    public record Daily(double[] r, LocalDate until) {
    }

    /** Taxa diaria em % ao dia (unidade PCT_DIA). */
    public static Daily dailyRate(List<LocalDate> axis, NavigableMap<LocalDate, BigDecimal> rates) {
        double[] r = new double[axis.size()];
        if (rates == null || rates.isEmpty()) {
            java.util.Arrays.fill(r, Double.NaN);
            return new Daily(r, null);
        }
        LocalDate first = rates.firstKey();
        LocalDate last = rates.lastKey();
        for (int i = 0; i < axis.size(); i++) {
            LocalDate prev = i > 0 ? axis.get(i - 1) : axis.get(0).minusDays(1);
            double f = 1;
            for (LocalDate x = prev.plusDays(1); !x.isAfter(axis.get(i)); x = x.plusDays(1)) {
                if (!BusinessCalendar.isBusinessDay(x) || x.isAfter(last)) {
                    continue;
                }
                BigDecimal v = rates.get(x);
                if (v == null || x.isBefore(first)) {
                    f = Double.NaN;
                    break;
                }
                f *= 1 + v.doubleValue() / 100;
            }
            r[i] = f - 1;
        }
        LocalDate end = axis.get(axis.size() - 1);
        return new Daily(r, last.isBefore(end) ? last : end);
    }

    /**
     * Taxa mensal em % no mes (unidade PCT_MES, datada no dia 1o), com spread real opcional em % a.a.
     */
    public static Daily monthlyRate(List<LocalDate> axis, NavigableMap<LocalDate, BigDecimal> months,
                                    double spreadPctYear) {
        double[] r = new double[axis.size()];
        if (months == null || months.isEmpty()) {
            java.util.Arrays.fill(r, Double.NaN);
            return new Daily(r, null);
        }
        double spreadDay = Math.pow(1 + spreadPctYear / 100, 1.0 / 252);
        Map<LocalDate, Double> monthDay = new java.util.HashMap<>();
        for (int i = 0; i < axis.size(); i++) {
            LocalDate prev = i > 0 ? axis.get(i - 1) : axis.get(0).minusDays(1);
            double f = 1;
            for (LocalDate x = prev.plusDays(1); !x.isAfter(axis.get(i)); x = x.plusDays(1)) {
                if (!BusinessCalendar.isBusinessDay(x)) {
                    continue;
                }
                LocalDate m = x.withDayOfMonth(1);
                BigDecimal v = months.get(m);
                if (v == null) {
                    f = Double.NaN;
                    break;
                }
                Double day = monthDay.computeIfAbsent(m, k ->
                        Math.pow(1 + v.doubleValue() / 100, 1.0 / BusinessCalendar.businessDaysInMonth(k)));
                f *= day * spreadDay;
            }
            r[i] = f - 1;
        }
        LocalDate lastMonth = months.lastKey();
        LocalDate until = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
        LocalDate end = axis.get(axis.size() - 1);
        return new Daily(r, until.isBefore(end) ? until : end);
    }

    /** Nivel (pontos ou preco). {@code today} = valor de hoje (preco atual), se houver. */
    public static Daily level(List<LocalDate> axis, NavigableMap<LocalDate, Double> levels, LocalDate todayDate,
                              Double todayValue) {
        double[] r = new double[axis.size()];
        LocalDate until = null;
        Double prevLevel = levelOn(axis, levels, axis.get(0).minusDays(1), -1);
        for (int i = 0; i < axis.size(); i++) {
            LocalDate d = axis.get(i);
            Double lv = d.equals(todayDate) && todayValue != null && todayValue > 0 ? todayValue
                    : levelOn(axis, levels, d, i);
            if (lv == null || prevLevel == null) {
                r[i] = Double.NaN;
            } else {
                r[i] = lv / prevLevel - 1;
                until = d;
            }
            prevLevel = lv;
        }
        return new Daily(r, until);
    }

    private static Double levelOn(List<LocalDate> axis, NavigableMap<LocalDate, Double> levels, LocalDate d, int i) {
        if (levels == null) {
            return null;
        }
        Map.Entry<LocalDate, Double> e = levels.floorEntry(d);
        if (e == null) {
            return null;
        }
        if (e.getKey().equals(d) || i < 0) {
            return i < 0 && BusinessCalendar.businessDaysBetween(e.getKey().plusDays(1), d)
                    > PortfolioTimeline.MAX_STALE_DAYS ? null : e.getValue();
        }
        return PortfolioTimelineBuilder.staleDays(axis, e.getKey(), i) <= PortfolioTimeline.MAX_STALE_DAYS
                ? e.getValue() : null;
    }
}
