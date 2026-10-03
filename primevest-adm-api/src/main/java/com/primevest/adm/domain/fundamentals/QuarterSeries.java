package com.primevest.adm.domain.fundamentals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Serie trimestral de uma companhia a partir dos seus DFP/ITR (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.1 e F6).
 *
 * <ul>
 *   <li><b>Trimestre isolado:</b> o valor do documento cujo periodo cabe num trimestre (coluna de 3 meses
 *   do ITR, ou o acumulado do 1T); senao o acumulado do exercicio menos o acumulado do documento do
 *   trimestre anterior com o mesmo inicio de exercicio. Assim sai o 4T (DFP - 9 meses) e a DFC e a DVA,
 *   que so trazem acumulado. Sem o documento anterior, nulo.</li>
 *   <li><b>12 meses:</b> o valor anual do documento (DFP no fim do exercicio); senao a soma dos 4 ultimos
 *   trimestres consecutivos, todos presentes — faltando um, nulo (nao estima).</li>
 *   <li><b>Posicao:</b> o balanco do proprio documento.</li>
 * </ul>
 *
 * <p>Exercicio fora do calendario (Camil, marco a fevereiro) e primeiro exercicio incompleto de companhia
 * nova caem nas mesmas regras: so datas e duracoes, nenhum mes fixo.</p>
 */
public final class QuarterSeries {

    /** Entre dois fins de trimestre consecutivos: 89 a 92 dias; folga para datas fora do fim do mes. */
    static final int QUARTER_MIN_DAYS = 80;
    static final int QUARTER_MAX_DAYS = 100;
    static final int YEAR_MIN_DAYS = 355;
    static final int YEAR_MAX_DAYS = 375;

    public enum Flow {
        REVENUE, EBIT, NET_INCOME, NET_INCOME_PARENT, FCO,
        /** Dividendos + JCP declarados (DVA, remuneracao de capitais proprios) — base do payout. */
        DIVIDENDS_DECLARED
    }

    private static final Map<FinancialMetric, Flow> FLOW_OF = Map.of(
            FinancialMetric.RECEITA, Flow.REVENUE,
            FinancialMetric.EBIT, Flow.EBIT,
            FinancialMetric.LUCRO_LIQUIDO, Flow.NET_INCOME,
            FinancialMetric.LUCRO_CONTROLADORA, Flow.NET_INCOME_PARENT,
            FinancialMetric.FCO, Flow.FCO,
            FinancialMetric.JCP_DECLARADO, Flow.DIVIDENDS_DECLARED,
            FinancialMetric.DIVIDENDOS_DECLARADOS, Flow.DIVIDENDS_DECLARED);

    /** Uma metrica gravada do documento; {@code start} nulo = posicao na data. */
    public record Value(FinancialMetric metric, LocalDate start, LocalDate end, BigDecimal value) {
    }

    /** Quantidade de acoes ja em unidades; nulos quando a escala nao foi conferida. */
    public record Shares(Long total, Long outstanding) {
        public static final Shares NONE = new Shares(null, null);
    }

    public record Doc(long id, String docType, LocalDate referenceDate, List<Value> values, Shares shares) {
    }

    public record Quarter(LocalDate periodEnd, LocalDate fiscalYearStart, long sourceDocId,
                          Map<Flow, BigDecimal> quarter, Map<Flow, BigDecimal> ttm,
                          BigDecimal equity, BigDecimal equityParent, BigDecimal totalAssets,
                          BigDecimal cash, BigDecimal grossDebt, Long sharesTotal, Long sharesOutstanding) {
    }

    private QuarterSeries() {
    }

    /** Documentos de uma companhia, em qualquer ordem; uma linha por data (DFP antes de ITR na mesma data). */
    public static List<Quarter> compute(List<Doc> docs) {
        TreeMap<LocalDate, Prepared> byDate = new TreeMap<>();
        for (Doc d : docs) {
            Prepared p = new Prepared(d);
            byDate.merge(d.referenceDate(), p, (a, b) -> "DFP".equals(a.doc.docType()) ? a : b);
        }

        List<Quarter> out = new ArrayList<>();
        Map<LocalDate, Map<Flow, BigDecimal>> quarters = new HashMap<>();
        for (Prepared p : byDate.values()) {
            LocalDate end = p.doc.referenceDate();
            Prepared prev = previousQuarter(byDate, end);

            Map<Flow, BigDecimal> q = new EnumMap<>(Flow.class);
            for (Flow f : Flow.values()) {
                BigDecimal v = isolated(p, prev, f);
                if (v != null) {
                    q.put(f, v);
                }
            }
            quarters.put(end, q);

            Map<Flow, BigDecimal> ttm = new EnumMap<>(Flow.class);
            List<LocalDate> last4 = lastFourQuarters(byDate, end);
            for (Flow f : Flow.values()) {
                BigDecimal v = p.annual(f);
                if (v == null && last4 != null) {
                    v = BigDecimal.ZERO;
                    for (LocalDate e : last4) {
                        BigDecimal x = quarters.get(e).get(f);
                        if (x == null) {
                            v = null;
                            break;
                        }
                        v = v.add(x);
                    }
                }
                if (v != null) {
                    ttm.put(f, v);
                }
            }

            BigDecimal equity = p.position(FinancialMetric.PL);
            BigDecimal minority = p.position(FinancialMetric.PL_NAO_CONTROLADORES);
            BigDecimal equityParent = equity == null ? null
                    : minority == null ? equity : equity.subtract(minority);
            Shares s = p.doc.shares() == null ? Shares.NONE : p.doc.shares();
            out.add(new Quarter(end, p.fiscalYearStart, p.doc.id(), q, ttm, equity, equityParent,
                    p.position(FinancialMetric.ATIVO_TOTAL), p.position(FinancialMetric.CAIXA),
                    p.position(FinancialMetric.DIVIDA_BRUTA), s.total(), s.outstanding()));
        }
        return out;
    }

    // ------------------------------------------------------------------ regras

    private static BigDecimal isolated(Prepared p, Prepared prev, Flow f) {
        TreeMap<LocalDate, BigDecimal> periods = p.flows.get(f);
        if (periods == null || periods.isEmpty()) {
            return null;
        }
        // periodo mais curto do documento: coluna de 3 meses do ITR, ou o proprio acumulado no 1T
        Map.Entry<LocalDate, BigDecimal> shortest = periods.lastEntry();
        if (days(shortest.getKey(), p.doc.referenceDate()) <= QUARTER_MAX_DAYS) {
            return shortest.getValue();
        }
        // acumulado do exercicio menos o acumulado do trimestre anterior, mesmo inicio
        if (prev == null) {
            return null;
        }
        Map<LocalDate, BigDecimal> prevPeriods = prev.flows.get(f);
        if (prevPeriods == null) {
            return null;
        }
        LocalDate start = periods.firstKey();
        BigDecimal before = prevPeriods.get(start);
        return before == null ? null : periods.get(start).subtract(before);
    }

    private static Prepared previousQuarter(TreeMap<LocalDate, Prepared> byDate, LocalDate end) {
        Map.Entry<LocalDate, Prepared> e = byDate.lowerEntry(end);
        return e != null && isQuarterApart(e.getKey(), end) ? e.getValue() : null;
    }

    /** As 4 datas (a propria e 3 anteriores) em trimestres consecutivos; nulo se a cadeia quebra. */
    private static List<LocalDate> lastFourQuarters(TreeMap<LocalDate, Prepared> byDate, LocalDate end) {
        List<LocalDate> dates = new ArrayList<>(List.of(end));
        LocalDate cur = end;
        for (int i = 0; i < 3; i++) {
            LocalDate before = byDate.lowerKey(cur);
            if (before == null || !isQuarterApart(before, cur)) {
                return null;
            }
            dates.add(before);
            cur = before;
        }
        return dates;
    }

    static boolean isQuarterApart(LocalDate earlier, LocalDate later) {
        long d = ChronoUnit.DAYS.between(earlier, later);
        return d >= QUARTER_MIN_DAYS && d <= QUARTER_MAX_DAYS;
    }

    /** Duracao do periodo, contando os dois extremos. */
    private static long days(LocalDate start, LocalDate end) {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /** Valores do documento por fluxo e inicio de periodo (so os que terminam na data do documento). */
    private static final class Prepared {
        final Doc doc;
        final Map<Flow, TreeMap<LocalDate, BigDecimal>> flows = new EnumMap<>(Flow.class);
        final Map<FinancialMetric, BigDecimal> positions = new EnumMap<>(FinancialMetric.class);
        final LocalDate fiscalYearStart;

        Prepared(Doc doc) {
            this.doc = doc;
            LocalDate earliest = null;
            for (Value v : doc.values()) {
                if (v.start() == null) {
                    positions.put(v.metric(), v.value());
                    continue;
                }
                Flow f = FLOW_OF.get(v.metric());
                if (f == null || !v.end().equals(doc.referenceDate())) {
                    continue;
                }
                flows.computeIfAbsent(f, k -> new TreeMap<>()).merge(v.start(), v.value(), BigDecimal::add);
                if (earliest == null || v.start().isBefore(earliest)) {
                    earliest = v.start();
                }
            }
            this.fiscalYearStart = earliest;
        }

        BigDecimal position(FinancialMetric m) {
            return positions.get(m);
        }

        BigDecimal annual(Flow f) {
            TreeMap<LocalDate, BigDecimal> periods = flows.get(f);
            if (periods == null) {
                return null;
            }
            for (Map.Entry<LocalDate, BigDecimal> e : periods.entrySet()) {
                long d = days(e.getKey(), doc.referenceDate());
                if (d >= YEAR_MIN_DAYS && d <= YEAR_MAX_DAYS) {
                    return e.getValue();
                }
            }
            return null;
        }
    }
}
