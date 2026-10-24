package com.activest.domain.pricehistory;

import com.activest.domain.corporateaction.StockEventType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Regras puras da cotacao historica (docs/planos/PLANO_TELA_EMISSORES.md, 2.3, B5 e D9): janela de cada
 * atalho, ajuste por eventos societarios e densidade dos pontos. Sem banco — testavel sozinha.
 */
public final class PriceSeries {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final int PRICE_SCALE = 6;
    /** Ate este tamanho de janela, ponto diario; ate {@link #WEEK_MAX_DAYS}, semanal; acima, mensal. */
    static final long DAY_MAX_DAYS = 370;
    static final long WEEK_MAX_DAYS = 5 * 366 + 5;

    private PriceSeries() {
    }

    /** Atalhos da tela; 1A e o padrao. */
    public enum Range {
        D1("1D"), D7("7D"), M1("1M"), M6("6M"), YTD("YTD"), Y1("1A"), Y5("5A"), MAX("MAX");

        private final String label;

        Range(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Range parse(String value) {
            String v = value.trim().toUpperCase(Locale.ROOT);
            for (Range r : values()) {
                if (r.label.equals(v) || r.name().equals(v)) {
                    return r;
                }
            }
            throw new IllegalArgumentException(value);
        }
    }

    public enum Interval { INTRADAY, DAY, WEEK, MONTH }

    /** Evento que muda a quantidade de cotas, com o fator como a B3 publica. */
    public record Event(LocalDate dataCom, StockEventType type, BigDecimal factor) {
    }

    /**
     * Inicio da janela do atalho, contado a partir do ultimo pregao. O 1D comeca no pregao
     * anterior ao ultimo (so ha fechamento: o dia e "fechamento anterior -> ultimo"); o MAX, no
     * primeiro pregao gravado.
     */
    public static LocalDate start(Range range, NavigableMap<LocalDate, BigDecimal> closes) {
        LocalDate last = closes.lastKey();
        LocalDate start = switch (range) {
            case D1 -> {
                LocalDate prev = closes.lowerKey(last);
                yield prev != null ? prev : last;
            }
            case D7 -> last.minusDays(7);
            case M1 -> last.minusMonths(1);
            case M6 -> last.minusMonths(6);
            case YTD -> last.withDayOfYear(1);
            case Y1 -> last.minusYears(1);
            case Y5 -> last.minusYears(5);
            case MAX -> closes.firstKey();
        };
        return start.isBefore(closes.firstKey()) ? closes.firstKey() : start;
    }

    /** Densidade pelo tamanho da janela (o mesmo vale para o periodo especifico "de/ate"). */
    public static Interval interval(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= DAY_MAX_DAYS) {
            return Interval.DAY;
        }
        return days <= WEEK_MAX_DAYS ? Interval.WEEK : Interval.MONTH;
    }

    /**
     * Fechamentos ajustados por desdobramento, grupamento e bonificacao (D9). O fechamento da
     * data-com ainda e "com direito": ele e todos os anteriores sao divididos pela razao do
     * evento. So contam eventos com data-com antes do ultimo pregao — evento cuja data-com e o
     * proprio ultimo pregao ainda nao produziu nenhum preco "ex". Sem ajuste por proventos.
     */
    public static NavigableMap<LocalDate, BigDecimal> adjust(NavigableMap<LocalDate, BigDecimal> closes,
                                                             List<Event> events) {
        NavigableMap<LocalDate, BigDecimal> out = new TreeMap<>();
        if (closes.isEmpty()) {
            return out;
        }
        LocalDate last = closes.lastKey();
        // razao acumulada por data-com, do evento mais recente para o mais antigo
        TreeMap<LocalDate, BigDecimal> cumulative = new TreeMap<>();
        List<Event> sorted = new ArrayList<>(events);
        sorted.sort((a, b) -> b.dataCom().compareTo(a.dataCom()));
        BigDecimal acc = BigDecimal.ONE;
        for (Event e : sorted) {
            BigDecimal q = e.type() == null ? null : e.type().quantityRatio(e.factor());
            if (q == null || !e.dataCom().isBefore(last)) {
                continue;
            }
            acc = acc.multiply(q, MC);
            cumulative.put(e.dataCom(), acc);
        }
        for (Map.Entry<LocalDate, BigDecimal> c : closes.entrySet()) {
            // eventos com data-com >= data do fechamento: o de data-com mais cedo carrega a razao de todos
            Map.Entry<LocalDate, BigDecimal> r = cumulative.ceilingEntry(c.getKey());
            BigDecimal price = r == null ? c.getValue() : c.getValue().divide(r.getValue(), MC);
            out.put(c.getKey(), price.setScale(PRICE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros());
        }
        return out;
    }

    /** Se algum fechamento da janela foi ajustado. */
    public static boolean adjustedIn(LocalDate from, LocalDate to, LocalDate last, List<Event> events) {
        for (Event e : events) {
            BigDecimal q = e.type() == null ? null : e.type().quantityRatio(e.factor());
            if (q != null && e.dataCom().isBefore(last) && !e.dataCom().isBefore(from)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pontos da janela na densidade pedida: o primeiro fechamento da janela e, depois, o ultimo
     * fechamento de cada semana ou mes (o ultimo ponto e sempre o ultimo pregao da janela).
     */
    public static List<Map.Entry<LocalDate, BigDecimal>> points(NavigableMap<LocalDate, BigDecimal> window,
                                                                Interval interval) {
        List<Map.Entry<LocalDate, BigDecimal>> out = new ArrayList<>();
        if (window.isEmpty()) {
            return out;
        }
        if (interval == Interval.DAY || interval == Interval.INTRADAY) {
            out.addAll(window.entrySet());
            return out;
        }
        Map<LocalDate, Map.Entry<LocalDate, BigDecimal>> buckets = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> e : window.entrySet()) {
            LocalDate key = interval == Interval.WEEK
                    ? e.getKey().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    : e.getKey().withDayOfMonth(1);
            buckets.put(key, e); // o ultimo do balde fica
        }
        Map.Entry<LocalDate, BigDecimal> first = window.firstEntry();
        out.add(first);
        for (Map.Entry<LocalDate, BigDecimal> e : buckets.values()) {
            if (!e.getKey().equals(first.getKey())) {
                out.add(e);
            }
        }
        return out;
    }
}
