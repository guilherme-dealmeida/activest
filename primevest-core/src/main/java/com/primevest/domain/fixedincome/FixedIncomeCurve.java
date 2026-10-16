package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Indexer;
import com.primevest.domain.asset.RateType;
import com.primevest.domain.calendar.BusinessCalendar;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Saldo de um titulo de renda fixa privada na curva contratada (docs/planos/PLANO_RENDA_FIXA_VALORIZACAO.md, 3.1-3.3;
 * D4-D9, D12, D14). Regra pura, sem banco.
 *
 * <p><b>Fluxo.</b> Aportes somam e resgates subtraem no proprio dia; o saldo rende em cada dia util pelo fator da forma
 * de remuneracao — a taxa do dia d rende de d para o dia util seguinte (criterio do CDI). Vale ate a data de
 * referencia (o ultimo CDI publicado, D14), inclusive.</p>
 *
 * <ul>
 *   <li><b>PREFIXADO</b> r% a.a.: {@code (1 + r)^(1/252)} por dia util.</li>
 *   <li><b>POS_FIXADO</b> p% do CDI/Selic: {@code 1 + taxa_do_dia × p}.</li>
 *   <li><b>HIBRIDO</b> CDI/Selic + s% a.a.: {@code (1 + taxa_do_dia) × (1 + s)^(1/252)}.</li>
 *   <li><b>IPCA, IGP-M, INPC</b> (D6): a inflacao do mes distribuida pelos dias uteis do mes,
 *   {@code (1 + I)^(1/DU_mes)}, vezes o spread diario (hibrido) — ou {@code (1 + I × p)^(1/DU_mes)} (pos-fixado).
 *   Mes ainda nao divulgado usa o ultimo indice publicado (D5) e o resultado sai marcado como estimado.</li>
 *   <li><b>TR</b>: sem curva (D9).</li>
 * </ul>
 *
 * <p><b>Resgate acima do saldo</b> (D4): o resgate prova que o titulo valia mais; o saldo fica no centavo que a regra
 * F exige e segue rendendo dali. <b>Vencimento</b> (D8): o titulo para de render no dia do vencimento (o ultimo fator
 * e o da vespera) e o resultado sai como vencido. Sem arredondamento no meio (D12): so o saldo final vai a 2 casas.</p>
 */
public final class FixedIncomeCurve {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal RESIDUE = new BigDecimal("0.01");
    private static final double DU_YEAR = 252.0;

    private FixedIncomeCurve() {
    }

    public enum Status {
        /** Saldo na curva ate a data de referencia. */
        OK,
        /** Vencido: o saldo parou no vencimento; falta lancar o resgate. */
        MATURED,
        /** Sem curva (TR, dado faltando, serie nao carregada): o motivo vem em {@code reason}. */
        UNSUPPORTED
    }

    /** Um movimento do titulo: compra positiva, resgate negativo. */
    public record Flow(LocalDate date, BigDecimal amount) {
    }

    /** O que foi contratado. */
    public record Terms(RateType rateType, Indexer indexer, BigDecimal rate, LocalDate maturity) {
    }

    /**
     * @param balance       saldo na curva (2 casas); nulo em UNSUPPORTED
     * @param referenceDate ate quando rendeu (o vencimento, se vencido)
     * @param estimated     usou indice de mes ainda nao divulgado (D5)
     * @param reason        motivo, em UNSUPPORTED
     */
    public record Result(Status status, BigDecimal balance, LocalDate referenceDate, boolean estimated, String reason) {

        static Result unsupported(String reason) {
            return new Result(Status.UNSUPPORTED, null, null, false, reason);
        }
    }

    /** Indexador de taxa diaria (CDI, Selic) — serie {@code daily}. */
    public static boolean isDaily(Indexer i) {
        return i == Indexer.CDI || i == Indexer.SELIC;
    }

    /** Indexador de inflacao mensal (IPCA, IGP-M, INPC) — serie {@code monthly}. */
    public static boolean isMonthly(Indexer i) {
        return i == Indexer.IPCA || i == Indexer.IGPM || i == Indexer.INPC;
    }

    /**
     * Saldo na curva.
     *
     * @param daily   taxa do dia (% a.d.) do CDI ou da Selic, por data — so para indexador diario
     * @param monthly variacao do mes (%) da inflacao, pelo dia 1o do mes — so para indexador mensal
     */
    public static Result value(Terms terms, List<Flow> flows, LocalDate referenceDate,
                               NavigableMap<LocalDate, BigDecimal> daily, NavigableMap<LocalDate, BigDecimal> monthly) {
        String problem = check(terms, daily, monthly);
        if (problem != null) {
            return Result.unsupported(problem);
        }
        List<Flow> sorted = new ArrayList<>(flows);
        sorted.sort(Comparator.comparing(Flow::date));
        if (sorted.isEmpty()) {
            return new Result(Status.OK, BigDecimal.ZERO.setScale(2), referenceDate, false, null);
        }
        LocalDate start = sorted.get(0).date();
        String beforeSeries = beforeSeries(terms, start, daily, monthly);
        if (beforeSeries != null) {
            return Result.unsupported(beforeSeries);
        }

        LocalDate maturity = terms.maturity();
        boolean matured = maturity != null && !maturity.isAfter(referenceDate);
        // ultimo dia que rende: a vespera do vencimento, ou a data de referencia
        LocalDate lastAccrual = matured ? maturity.minusDays(1) : referenceDate;
        LocalDate lastFlow = sorted.get(sorted.size() - 1).date();
        LocalDate end = lastFlow.isAfter(lastAccrual) ? lastFlow : lastAccrual;

        NavigableMap<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (Flow f : sorted) {
            byDay.merge(f.date(), f.amount(), BigDecimal::add);
        }

        BigDecimal spreadDay = spreadDay(terms);

        BigDecimal balance = BigDecimal.ZERO;
        boolean[] estimated = {false};
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            BigDecimal moved = byDay.get(d);
            if (moved != null) {
                balance = balance.add(moved, MC);
                if (moved.signum() < 0 && balance.compareTo(RESIDUE) < 0) {
                    balance = RESIDUE; // D4: o resgate prova o valor
                }
            }
            if (d.isAfter(lastAccrual) || !BusinessCalendar.isBusinessDay(d) || balance.signum() == 0) {
                continue;
            }
            BigDecimal factor = dayFactor(terms, d, daily, monthly, spreadDay, estimated);
            if (factor == null) {
                lastAccrual = daily.lastKey(); // dia util sem taxa: a serie acabou antes da data de referencia
                continue;
            }
            balance = balance.multiply(factor, MC);
        }
        return new Result(matured ? Status.MATURED : Status.OK, balance.setScale(2, RoundingMode.HALF_EVEN),
                matured ? maturity : lastAccrual, estimated[0], null);
    }

    /** O spread diario do prefixado e do hibrido ({@code (1 + r)^(1/252)}); 1 nos demais. */
    static BigDecimal spreadDay(Terms terms) {
        if (terms.rateType() == RateType.HIBRIDO || terms.rateType() == RateType.PREFIXADO) {
            return new BigDecimal(Math.pow(1 + terms.rate().doubleValue() / 100, 1 / DU_YEAR), MC);
        }
        return BigDecimal.ONE;
    }

    /**
     * O fator de um dia util — a taxa do dia {@code d} rende de {@code d} para o dia util seguinte. Usado pelo saldo
     * unico ({@link #value}) e pelos lotes ({@link FixedIncomeLots}), que tem de render igual.
     *
     * @param estimated marcado quando usa indice de mes ainda nao divulgado (D5)
     * @return o fator; nulo quando a serie diaria acabou antes deste dia (o titulo para de render ali)
     */
    static BigDecimal dayFactor(Terms terms, LocalDate d, NavigableMap<LocalDate, BigDecimal> daily,
                                NavigableMap<LocalDate, BigDecimal> monthly, BigDecimal spreadDay, boolean[] estimated) {
        BigDecimal rate = terms.rate();
        if (terms.rateType() == RateType.PREFIXADO) {
            return spreadDay;
        }
        if (isDaily(terms.indexer())) {
            BigDecimal r = daily.get(d);
            if (r == null) {
                Map.Entry<LocalDate, BigDecimal> last = daily.lastEntry();
                if (d.isAfter(last.getKey())) {
                    return null;
                }
                r = BigDecimal.ZERO;
            }
            BigDecimal day = r.divide(HUNDRED, MC);
            return terms.rateType() == RateType.POS_FIXADO
                    ? BigDecimal.ONE.add(day.multiply(rate.divide(HUNDRED, MC), MC), MC)
                    : BigDecimal.ONE.add(day, MC).multiply(spreadDay, MC);
        }
        LocalDate month = d.withDayOfMonth(1);
        BigDecimal inflation = monthly.get(month);
        if (inflation == null) {
            inflation = monthly.lastEntry().getValue(); // D5: ultimo publicado
            estimated[0] = true;
        }
        double i = inflation.doubleValue() / 100;
        if (terms.rateType() == RateType.POS_FIXADO) {
            i = i * rate.doubleValue() / 100;
        }
        int du = BusinessCalendar.businessDaysInMonth(d);
        BigDecimal monthDay = new BigDecimal(Math.pow(1 + i, 1.0 / du), MC);
        return terms.rateType() == RateType.POS_FIXADO ? monthDay : monthDay.multiply(spreadDay, MC);
    }

    /** A primeira aplicacao e anterior a serie do indice: sem curva. Nulo = tudo certo. */
    static String beforeSeries(Terms terms, LocalDate start, NavigableMap<LocalDate, BigDecimal> daily,
                               NavigableMap<LocalDate, BigDecimal> monthly) {
        if (isDaily(terms.indexer()) && terms.rateType() != RateType.PREFIXADO && start.isBefore(daily.firstKey())) {
            return "aplicação anterior à série do " + terms.indexer() + " (" + daily.firstKey() + ")";
        }
        if (isMonthly(terms.indexer()) && terms.rateType() != RateType.PREFIXADO
                && start.withDayOfMonth(1).isBefore(monthly.firstKey())) {
            return "aplicação anterior à série do " + terms.indexer() + " (" + monthly.firstKey() + ")";
        }
        return null;
    }

    static String check(Terms t, NavigableMap<LocalDate, BigDecimal> daily,
                                NavigableMap<LocalDate, BigDecimal> monthly) {
        if (t.rateType() == null) {
            return "sem forma de remuneração cadastrada";
        }
        if (t.rate() == null) {
            return "sem taxa cadastrada";
        }
        if (t.rateType() == RateType.PREFIXADO) {
            return null;
        }
        if (t.indexer() == null) {
            return "sem índice cadastrado";
        }
        if (t.indexer() == Indexer.TR) {
            return "a TR ainda não tem curva no PrimeVest";
        }
        if (isDaily(t.indexer()) && (daily == null || daily.isEmpty())) {
            return "série do " + t.indexer() + " não carregada";
        }
        if (isMonthly(t.indexer()) && (monthly == null || monthly.isEmpty())) {
            return "série do " + t.indexer() + " não carregada";
        }
        return null;
    }
}
