package com.activest.domain.performance;

import com.activest.domain.asset.AssetLogos;
import com.activest.domain.performance.PerformanceDTOs.BenchmarkResultDTO;
import com.activest.domain.performance.PerformanceDTOs.BreakdownDTO;
import com.activest.domain.performance.PerformanceDTOs.BreakdownRowDTO;
import com.activest.domain.performance.PerformanceDTOs.CoverageDTO;
import com.activest.domain.performance.PerformanceDTOs.GapDTO;
import com.activest.domain.performance.PerformanceDTOs.PeriodRowDTO;
import com.activest.domain.performance.PerformanceDTOs.PeriodicDTO;
import com.activest.domain.performance.PerformanceDTOs.RiskDTO;
import com.activest.domain.performance.PerformanceDTOs.SeriesDTO;
import com.activest.domain.performance.PerformanceDTOs.SeriesPointDTO;
import com.activest.domain.performance.PerformanceDTOs.SummaryDTO;
import com.activest.domain.performance.PerformanceDTOs.TableDTO;
import com.activest.domain.performance.PerformanceDTOs.TableRowDTO;
import com.activest.domain.performance.PerformanceDTOs.WealthDTO;
import com.activest.domain.performance.PerformanceDTOs.WealthPointDTO;
import com.activest.exception.BusinessException;
import com.activest.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * O motor da Rentabilidade (docs/planos/PLANO_RENTABILIDADE.md, secoes 2, 4 e 5): recorta a linha do tempo, escolhe o
 * periodo, compoe os retornos e devolve cards, grafico, periodos, tabela, analise por classe e ativo, patrimonio e
 * risco — tudo das mesmas funcoes (R15), para o mesmo numero aparecer em todo lugar.
 */
@Service
public class PerformanceService {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> TYPES_WITH_LOGO = Set.of("ACAO", "FII", "ETF", "BDR", "CRIPTO");
    /** R6 e R14: XIRR e risco so com ao menos um ano de historia no periodo. */
    static final int MIN_DAYS_YEAR = 365;

    private final PortfolioTimelineBuilder builder;
    private final BenchmarkData benchmarkData;
    private final AssetLogos assetLogos;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PerformanceService(PortfolioTimelineBuilder builder, BenchmarkData benchmarkData, AssetLogos assetLogos) {
        this(builder, benchmarkData, assetLogos, Clock.system(ZONE));
    }

    PerformanceService(PortfolioTimelineBuilder builder, BenchmarkData benchmarkData, AssetLogos assetLogos,
                       Clock clock) {
        this.builder = builder;
        this.benchmarkData = benchmarkData;
        this.assetLogos = assetLogos;
        this.clock = clock;
    }

    /** O pedido: recorte, periodo (atalho ou de/ate) e benchmarks. */
    public record Request(PerformanceScope scope, String range, LocalDate from, LocalDate to,
                          List<BenchmarkData.Ref> benchmarks) {
    }

    /** Atalhos de periodo (D7): os da cotacao, sem 1D e 7D. Padrao MAX ("desde o inicio"). */
    enum Range {
        M1("1M"), M6("6M"), YTD("YTD"), Y1("1A"), Y5("5A"), MAX("MAX");

        final String label;

        Range(String label) {
            this.label = label;
        }

        static Range parse(String s) {
            if (s == null || s.isBlank()) {
                return MAX;
            }
            for (Range r : values()) {
                if (r.label.equalsIgnoreCase(s.trim())) {
                    return r;
                }
            }
            throw new BusinessException("Período inválido: " + s + " (use 1M, 6M, YTD, 1A, 5A ou MAX)");
        }

        /** Dia-base do atalho (o acumulado conta a partir do dia seguinte); nulo = desde o inicio. */
        LocalDate base(LocalDate end) {
            return switch (this) {
                case M1 -> end.minusMonths(1);
                case M6 -> end.minusMonths(6);
                case YTD -> LocalDate.of(end.getYear() - 1, 12, 31);
                case Y1 -> end.minusYears(1);
                case Y5 -> end.minusYears(5);
                case MAX -> null;
            };
        }
    }

    /** Tudo o que as respostas precisam, calculado uma vez por pedido. */
    private record Ctx(PortfolioTimeline t, ScopeSeries s, double[] r, int from, int to, int firstActive,
                       Map<String, BenchmarkData.Resolved> bench, BenchmarkSeries.Daily cdi,
                       BenchmarkSeries.Daily ipca, CoverageDTO coverage) {

        boolean empty() {
            return coverage.empty();
        }

        LocalDate date(int i) {
            return t.dates().get(i);
        }
    }

    // ------------------------------------------------------------------ contexto

    private Ctx context(UUID userId, Request req) {
        Range range = req.from() != null || req.to() != null ? null : Range.parse(req.range());
        if (req.from() != null && req.to() != null && req.to().isBefore(req.from())) {
            throw new BusinessException("Período inválido: a data final é anterior à inicial");
        }
        PortfolioTimeline t = builder.timeline(userId);
        PerformanceScope scope = req.scope() != null ? req.scope() : PerformanceScope.all();
        if (t.isEmpty()) {
            if (scope.positionId() != null || scope.ticker() != null) {
                throw new ResourceNotFoundException("Posição não encontrada");
            }
            return emptyCtx(t, scope);
        }
        ScopeSeries s = ScopeSeries.of(t, scope.filter(), scope.basis());
        if ((scope.positionId() != null || scope.ticker() != null) && s.members.isEmpty()) {
            throw new ResourceNotFoundException("Posição não encontrada");
        }
        int[] span = s.span();
        if (span[0] < 0) {
            return emptyCtx(t, scope);
        }
        LocalDate end = req.to() != null ? req.to() : t.today();
        int to = Math.min(t.indexOnOrBefore(end), t.dates().size() - 1);
        int from;
        if (req.from() != null) {
            from = t.indexOnOrAfter(req.from());
        } else {
            LocalDate base = range.base(to >= 0 ? t.dates().get(to) : end);
            from = base == null ? span[0] : t.indexOnOrAfter(base.plusDays(1));
        }
        from = Math.max(from, span[0]);
        if (to < from) {
            return emptyCtx(t, scope);
        }
        double[] r = ReturnMath.daily(s);
        List<BenchmarkData.Ref> refs = req.benchmarks() == null || req.benchmarks().isEmpty()
                ? List.of(new BenchmarkData.Ref(BenchmarkData.DEFAULT_CODE, null)) : req.benchmarks();
        Map<String, BenchmarkData.Resolved> bench = benchmarkData.resolve(refs, t.dates(), t.today());
        BenchmarkSeries.Daily cdi = benchmarkData.byCode("CDI", t.dates(), t.today());
        BenchmarkSeries.Daily ipca = benchmarkData.byCode("IPCA", t.dates(), t.today());

        List<String> missing = new ArrayList<>(s.missingTickers(from, to));
        List<GapDTO> gaps = s.gaps(from, to).stream().map(g -> new GapDTO(g.ticker(), g.from(), g.to(),
                switch (g.status()) {
                    case MISSING -> "SEM_PRECO";
                    case STALE -> "ULTIMO_NEGOCIO";
                    case ESTIMATED -> "ESTIMADO";
                    default -> g.status().name();
                })).toList();
        boolean available = true;
        for (int i = from; i <= to; i++) {
            if (s.unavailable[i]) {
                available = false;
                break;
            }
        }
        LocalDate realUntil = ipca.until() != null && ipca.until().isBefore(t.dates().get(to)) ? ipca.until() : null;
        boolean basisApplies = s.members.stream().anyMatch(PortfolioTimeline.PositionSeries::basisSensitive);
        LocalDate baseDate = from > 0 ? t.dates().get(from - 1) : t.dates().get(0).minusDays(1);
        CoverageDTO cov = new CoverageDTO(t.dates().get(span[0]), t.dates().get(from), baseDate, t.dates().get(to),
                available, missing, gaps, realUntil, basisApplies, scope.basis().name(), scope.reservation().name(),
                false);
        return new Ctx(t, s, r, from, to, span[0], bench, cdi, ipca, cov);
    }

    private Ctx emptyCtx(PortfolioTimeline t, PerformanceScope scope) {
        CoverageDTO cov = new CoverageDTO(null, null, null, null, false, List.of(), List.of(), null, false,
                scope.basis().name(), scope.reservation().name(), true);
        return new Ctx(t, null, new double[0], 0, -1, -1, Map.of(), null, null, cov);
    }

    // ------------------------------------------------------------------ resumo (cards)

    public SummaryDTO summary(UUID userId, Request req) {
        Ctx c = context(userId, req);
        if (c.empty()) {
            return new SummaryDTO(c.coverage(), null, null, null, null, null, null, null, null, null,
                    "Sem dados no período", null, null, null, null, null, null, List.of());
        }
        Double twr = ReturnMath.chain(c.r(), c.from(), c.to());
        List<BenchmarkResultDTO> benches = benchResults(c, c.from(), c.to());
        BenchmarkResultDTO primary = benches.isEmpty() ? null : benches.get(0);

        double[] inf = ReturnMath.chainUntilGap(c.ipca().r(), c.from(), c.to());
        Double inflation = Double.isNaN(inf[0]) ? null : inf[0];
        Double nominalUntil = inflation == null ? null : ReturnMath.chain(c.r(), c.from(), (int) inf[1]);
        Double real = ReturnMath.real(nominalUntil, inflation);
        LocalDate realUntil = inflation == null ? null : c.date((int) inf[1]);

        Double cdi = chainBench(c.cdi().r(), c.from(), c.to());
        Double pctCdi = ReturnMath.percentOfCdi(twr, cdi);

        ScopeSeries s = c.s();
        double start = c.from() > 0 ? s.value[c.from() - 1] : 0;
        double end = s.value[c.to()];
        double buys = 0;
        double sells = 0;
        double dists = 0;
        for (int i = c.from(); i <= c.to(); i++) {
            buys += s.buy[i];
            sells += s.sell[i];
            dists += s.dist[i];
        }
        boolean ok = c.coverage().available();
        String xirrNote = null;
        Double xirr = null;
        long days = ChronoUnit.DAYS.between(c.coverage().baseDate(), c.date(c.to()));
        if (!ok) {
            xirrNote = "Indisponível: há ativo sem preço no período";
        } else if (days < MIN_DAYS_YEAR) {
            xirrNote = "Exige ao menos 12 meses de histórico no período";
        } else {
            xirr = Xirr.of(cashFlows(c, start, end));
            if (xirr == null) {
                xirrNote = "Sem solução para os fluxos do período";
            }
        }
        return new SummaryDTO(c.coverage(), pct(twr), primary, primary != null ? primary.diff() : null, pct(real),
                realUntil, pct(inflation), round(pctCdi, 2), pct(cdi), pct(xirr), xirrNote,
                ok ? money(start) : null, ok ? money(end) : null, money(buys), money(sells), money(dists),
                ok ? money(end - start - buys + sells + dists) : null, benches);
    }

    /** R6: aportes (-), retiradas (+), proventos no pagamento (+), saldo final + proventos a receber (+). */
    private List<Xirr.CashFlow> cashFlows(Ctx c, double start, double end) {
        List<Xirr.CashFlow> flows = new ArrayList<>();
        ScopeSeries s = c.s();
        if (start > 0.005) {
            flows.add(new Xirr.CashFlow(c.coverage().baseDate(), -start));
        }
        for (int i = c.from(); i <= c.to(); i++) {
            if (s.buy[i] != 0) {
                flows.add(new Xirr.CashFlow(c.date(i), -s.buy[i]));
            }
            if (s.sell[i] != 0) {
                flows.add(new Xirr.CashFlow(c.date(i), s.sell[i]));
            }
        }
        LocalDate last = c.date(c.to());
        double pending = 0;
        for (PortfolioTimeline.PositionSeries p : s.members) {
            for (PortfolioTimeline.Distribution d : p.distributions()) {
                if (d.index() < c.from() || d.index() > c.to()) {
                    continue;
                }
                double v = s.basis == PerformanceBasis.GROSS ? d.gross() : d.net();
                LocalDate pay = d.paymentDate() != null ? d.paymentDate() : d.exDate();
                if (pay.isAfter(last)) {
                    pending += v;
                } else {
                    flows.add(new Xirr.CashFlow(pay.isBefore(d.exDate()) ? d.exDate() : pay, v));
                }
            }
        }
        flows.add(new Xirr.CashFlow(last, end + pending));
        return flows;
    }

    private List<BenchmarkResultDTO> benchResults(Ctx c, int from, int to) {
        List<BenchmarkResultDTO> out = new ArrayList<>();
        for (Map.Entry<String, BenchmarkData.Resolved> e : c.bench().entrySet()) {
            double[] b = ReturnMath.chainUntilGap(e.getValue().daily().r(), from, to);
            Double value = Double.isNaN(b[0]) ? null : b[0];
            int until = (int) b[1];
            Double portfolio = value == null ? null : ReturnMath.chain(c.r(), from, until);
            out.add(new BenchmarkResultDTO(e.getKey(), e.getValue().name(), pct(value),
                    value == null ? null : c.date(until), pct(ReturnMath.diff(portfolio, value)),
                    value != null && until < to));
        }
        return out;
    }

    /** Benchmark composto no intervalo inteiro (taxa diaria: depois da ultima publicada o fator e 1). */
    private static Double chainBench(double[] r, int from, int to) {
        double[] b = ReturnMath.chainUntilGap(r, from, to);
        return Double.isNaN(b[0]) || (int) b[1] < to ? null : b[0];
    }

    // ------------------------------------------------------------------ grafico acumulado

    public SeriesDTO series(UUID userId, Request req) {
        Ctx c = context(userId, req);
        if (c.empty()) {
            return new SeriesDTO(c.coverage(), List.of(), "DIARIA", List.of());
        }
        double[] cum = ReturnMath.cumulative(c.r(), c.from(), c.to());
        Map<String, double[]> bcum = new LinkedHashMap<>();
        c.bench().forEach((k, v) -> bcum.put(k, ReturnMath.cumulative(v.daily().r(), c.from(), c.to())));
        String density = density(c.coverage().baseDate(), c.date(c.to()));
        List<Integer> keep = sample(c, density);
        List<SeriesPointDTO> points = new ArrayList<>();
        Map<String, Double> zero = new LinkedHashMap<>();
        bcum.keySet().forEach(k -> zero.put(k, 0.0));
        points.add(new SeriesPointDTO(c.coverage().baseDate(), 0.0, zero, false, false, false));
        int prev = c.from() - 1;
        for (int i : keep) {
            Map<String, Double> bs = new LinkedHashMap<>();
            for (Map.Entry<String, double[]> e : bcum.entrySet()) {
                bs.put(e.getKey(), pct(nan(e.getValue()[i - c.from()])));
            }
            boolean unavailable = false;
            boolean stale = false;
            boolean estimated = false;
            for (int j = prev + 1; j <= i; j++) {
                unavailable |= c.s().unavailable[j];
                stale |= c.s().stale[j];
                estimated |= c.s().estimated[j];
            }
            points.add(new SeriesPointDTO(c.date(i), pct(nan(cum[i - c.from()])), bs, unavailable, stale, estimated));
            prev = i;
        }
        return new SeriesDTO(c.coverage(), benchResults(c, c.from(), c.to()), density, points);
    }

    /** Densidade dos graficos de cotacao (PriceSeries): diario ate ~1 ano, semanal ate ~5, mensal acima. */
    static String density(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        return days <= 400 ? "DIARIA" : days <= 5 * 366 ? "SEMANAL" : "MENSAL";
    }

    /** Os indices mantidos na densidade: o ultimo de cada semana ou mes, e sempre o ultimo do periodo. */
    private static List<Integer> sample(Ctx c, String density) {
        List<Integer> keep = new ArrayList<>();
        for (int i = c.from(); i <= c.to(); i++) {
            if (i == c.to() || "DIARIA".equals(density)) {
                keep.add(i);
                continue;
            }
            LocalDate d = c.date(i);
            LocalDate n = c.date(i + 1);
            boolean last = "SEMANAL".equals(density)
                    ? d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != n.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
                    || d.get(IsoFields.WEEK_BASED_YEAR) != n.get(IsoFields.WEEK_BASED_YEAR)
                    : d.getMonthValue() != n.getMonthValue() || d.getYear() != n.getYear();
            if (last) {
                keep.add(i);
            }
        }
        return keep;
    }

    // ------------------------------------------------------------------ por periodo e mapa de calor

    public PeriodicDTO periodic(UUID userId, Request req, String granularity) {
        String g = granularity == null ? "MONTH" : granularity.trim().toUpperCase(Locale.ROOT);
        Function<LocalDate, String> key = switch (g) {
            case "MONTH" -> d -> String.format("%d-%02d", d.getYear(), d.getMonthValue());
            case "QUARTER" -> d -> d.getYear() + "-T" + ((d.getMonthValue() - 1) / 3 + 1);
            case "YEAR" -> d -> String.valueOf(d.getYear());
            default -> throw new BusinessException("Granularidade inválida: " + granularity
                    + " (use MONTH, QUARTER ou YEAR)");
        };
        Ctx c = context(userId, req);
        if (c.empty()) {
            return new PeriodicDTO(c.coverage(), g, List.of(), List.of());
        }
        List<PeriodRowDTO> rows = new ArrayList<>();
        int start = c.from();
        for (int i = c.from(); i <= c.to(); i++) {
            boolean close = i == c.to() || !key.apply(c.date(i)).equals(key.apply(c.date(i + 1)));
            if (!close) {
                continue;
            }
            String k = key.apply(c.date(i));
            Map<String, Double> bs = new LinkedHashMap<>();
            for (Map.Entry<String, BenchmarkData.Resolved> e : c.bench().entrySet()) {
                bs.put(e.getKey(), pct(chainBench(e.getValue().daily().r(), start, i)));
            }
            LocalDate pFrom = c.date(start);
            LocalDate pTo = c.date(i);
            boolean partial = (start == c.from() && startsLate(g, pFrom)) || (i == c.to() && endsEarly(g, pTo));
            rows.add(new PeriodRowDTO(k, pFrom, pTo, partial, pct(ReturnMath.chain(c.r(), start, i)), bs));
            start = i + 1;
        }
        return new PeriodicDTO(c.coverage(), g, benchResults(c, c.from(), c.to()), rows);
    }

    /** Ha pregao antes de {@code d} no mesmo periodo (o grupo comecou no meio dele). */
    private static boolean startsLate(String g, LocalDate d) {
        for (LocalDate x = d.minusDays(1); samePeriod(g, x, d); x = x.minusDays(1)) {
            if (com.activest.domain.calendar.BusinessCalendar.isBusinessDay(x)) {
                return true;
            }
        }
        return false;
    }

    /** Ha pregao depois de {@code d} no mesmo periodo (o grupo terminou antes do fim dele). */
    private static boolean endsEarly(String g, LocalDate d) {
        for (LocalDate x = d.plusDays(1); samePeriod(g, x, d); x = x.plusDays(1)) {
            if (com.activest.domain.calendar.BusinessCalendar.isBusinessDay(x)) {
                return true;
            }
        }
        return false;
    }

    private static boolean samePeriod(String g, LocalDate a, LocalDate b) {
        return switch (g) {
            case "MONTH" -> a.getYear() == b.getYear() && a.getMonthValue() == b.getMonthValue();
            case "QUARTER" -> a.getYear() == b.getYear() && (a.getMonthValue() - 1) / 3 == (b.getMonthValue() - 1) / 3;
            default -> a.getYear() == b.getYear();
        };
    }

    // ------------------------------------------------------------------ tabela comparativa

    public TableDTO table(UUID userId, Request req) {
        Ctx c = context(userId, req);
        if (c.empty()) {
            return new TableDTO(c.coverage(), null, null, null, List.of());
        }
        LocalDate end = c.date(c.to());
        int month = window(c, end.withDayOfMonth(1));
        int year = window(c, LocalDate.of(end.getYear(), 1, 1));
        int m12 = window(c, end.minusYears(1).plusDays(1));
        List<TableRowDTO> rows = new ArrayList<>();
        Double period = ReturnMath.chain(c.r(), c.from(), c.to());
        rows.add(new TableRowDTO("CARTEIRA", "Carteira", true, pct(ReturnMath.chain(c.r(), month, c.to())),
                pct(ReturnMath.chain(c.r(), year, c.to())), pct(ReturnMath.chain(c.r(), m12, c.to())), pct(period),
                null, null));
        for (Map.Entry<String, BenchmarkData.Resolved> e : c.bench().entrySet()) {
            // IPCA para no ultimo mes publicado: cada coluna vai ate onde ha dado (marcado com "ate"), e a diferenca
            // compara a carteira no mesmo intervalo
            double[] r = e.getValue().daily().r();
            double[] p = ReturnMath.chainUntilGap(r, c.from(), c.to());
            Double pv = Double.isNaN(p[0]) ? null : p[0];
            int until = (int) p[1];
            Double portfolioUntil = pv == null ? null : ReturnMath.chain(c.r(), c.from(), until);
            rows.add(new TableRowDTO(e.getKey(), e.getValue().name(), false, pct(partial(r, month, c.to())),
                    pct(partial(r, year, c.to())), pct(partial(r, m12, c.to())), pct(pv),
                    pct(ReturnMath.diff(portfolioUntil, pv)),
                    pv != null && until < c.to() ? c.date(until) : null));
        }
        return new TableDTO(c.coverage(), c.date(month), c.date(year), c.date(m12), rows);
    }

    /** Benchmark composto de {@code from} ate o ultimo dia com dado (nulo se nenhum). */
    private static Double partial(double[] r, int from, int to) {
        double[] b = ReturnMath.chainUntilGap(r, from, to);
        return Double.isNaN(b[0]) ? null : b[0];
    }

    /** Primeiro dia do eixo a partir de {@code start}, sem passar antes do primeiro dia com capital. */
    private static int window(Ctx c, LocalDate start) {
        return Math.max(c.t().indexOnOrAfter(start), c.firstActive());
    }

    // ------------------------------------------------------------------ classe e ativo (E05)

    public BreakdownDTO breakdown(UUID userId, Request req, String level) {
        String lv = level == null ? "POSITION" : level.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TYPE", "CATEGORY", "POSITION", "ISSUER").contains(lv)) {
            throw new BusinessException("Nível inválido: " + level + " (use TYPE, CATEGORY, POSITION ou ISSUER)");
        }
        Ctx c = context(userId, req);
        if (c.empty()) {
            return new BreakdownDTO(c.coverage(), lv, null, null, List.of());
        }
        ScopeSeries s = c.s();
        Map<String, List<PortfolioTimeline.PositionSeries>> groups = new LinkedHashMap<>();
        for (PortfolioTimeline.PositionSeries p : s.members) {
            String k = switch (lv) {
                case "TYPE" -> p.typeName();
                case "CATEGORY" -> String.valueOf(p.categoryId());
                case "ISSUER" -> p.issuerGroup() != null ? "I:" + p.issuerGroup() : null;
                default -> p.id().toString();
            };
            if (k != null) {
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(p);
            }
        }
        Double total = ReturnMath.chain(c.r(), c.from(), c.to());
        double kTotal = total == null ? 1 : Math.abs(total) < 1e-12 ? 1 : Math.log1p(total) / total;
        List<BreakdownRowDTO> rows = new ArrayList<>();
        double totalGain = 0;
        for (Map.Entry<String, List<PortfolioTimeline.PositionSeries>> e : groups.entrySet()) {
            List<PortfolioTimeline.PositionSeries> members = e.getValue();
            Set<PortfolioTimeline.PositionSeries> set = new LinkedHashSet<>(members);
            ScopeSeries g = ScopeSeries.of(c.t(), set::contains, s.basis);
            double[] rg = ReturnMath.daily(g);
            int first = -1;
            int last = -1;
            boolean unavailable = false;
            double gain = 0;
            double contrib = 0;
            double weight = 0;
            int weightDays = 0;
            for (int i = c.from(); i <= c.to(); i++) {
                boolean active = g.active(i) || g.value[i] > 0.005;
                if (active) {
                    if (first < 0) {
                        first = i;
                    }
                    last = i;
                }
                if (g.unavailable[i]) {
                    unavailable = true;
                }
                double gi = g.gain(i);
                gain += gi;
                if (s.active(i) && !Double.isNaN(c.r()[i])) {
                    double den = s.denominator(i);
                    double ri = c.r()[i];
                    double k = Math.abs(ri) < 1e-12 ? 1 : Math.log1p(ri) / ri;
                    contrib += k / kTotal * gi / den;
                    weight += g.denominator(i) / den;
                    weightDays++;
                }
            }
            totalGain += gain;
            Double individual = first < 0 ? null : ReturnMath.chain(rg, first, last);
            PortfolioTimeline.PositionSeries p0 = members.get(0);
            boolean closed = g.value[c.to()] <= 0.005;
            String label;
            String detail = null;
            switch (lv) {
                case "TYPE" -> label = p0.typeDescription() != null ? p0.typeDescription() : p0.typeName();
                case "CATEGORY" -> {
                    label = p0.categoryName() != null ? p0.categoryName() : "Sem categoria";
                    detail = p0.typeDescription();
                }
                case "ISSUER" -> {
                    label = p0.issuerGroup();
                    detail = members.size() + (members.size() == 1 ? " título" : " títulos");
                }
                default -> {
                    label = p0.ticker();
                    detail = p0.noteLabel() != null ? p0.noteLabel() : p0.name();
                }
            }
            boolean posLevel = "POSITION".equals(lv);
            rows.add(new BreakdownRowDTO(e.getKey(), label, detail, p0.typeName(), p0.typeDescription(),
                    "TYPE".equals(lv) ? null : p0.categoryId(), "TYPE".equals(lv) ? null : p0.categoryName(),
                    posLevel ? p0.id() : null, posLevel ? p0.ticker() : null,
                    posLevel && TYPES_WITH_LOGO.contains(p0.typeName()) ? assetLogos.hasLogo(p0.ticker()) : null,
                    pct(individual), unavailable ? null : money(gain),
                    unavailable || total == null ? null : pct(contrib),
                    weightDays > 0 ? round(weight / weightDays * 100, 4) : null,
                    money(g.value[c.to()]), closed, unavailable,
                    first < 0 ? null : c.date(first), last < 0 ? null : c.date(last)));
        }
        rows.sort((a, b) -> Double.compare(b.contribution() != null ? b.contribution() : -1e9,
                a.contribution() != null ? a.contribution() : -1e9));
        return new BreakdownDTO(c.coverage(), lv, pct(total), c.coverage().available() ? money(totalGain) : null,
                rows);
    }

    // ------------------------------------------------------------------ patrimonio (E06)

    public WealthDTO wealth(UUID userId, Request req) {
        Ctx c = context(userId, req);
        if (c.empty()) {
            return new WealthDTO(c.coverage(), null, "DIARIA", List.of());
        }
        ScopeSeries s = c.s();
        double[] ipca = c.ipca().r();
        int n = s.size();
        // indice de precos no eixo, parado depois do ultimo mes publicado
        double[] level = new double[n];
        double lv = 1;
        int lastIpca = -1;
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(ipca[i])) {
                lv *= 1 + ipca[i];
                lastIpca = i;
            }
            level[i] = lv;
        }
        double baseLevel = lastIpca >= 0 ? level[lastIpca] : 1;
        LocalDate realBase = lastIpca >= 0 ? c.date(lastIpca) : null;
        double invested = 0;
        double investedReal = 0;
        double dists = 0;
        double sells = 0;
        double buys = 0;
        double[][] acc = new double[n][5];
        for (int i = 0; i <= c.to(); i++) {
            double defl = baseLevel / level[i];
            invested += s.buy[i] - s.sell[i];
            investedReal += (s.buy[i] - s.sell[i]) * defl;
            dists += s.dist[i];
            sells += s.sell[i];
            buys += s.buy[i];
            acc[i] = new double[]{invested, investedReal, dists, s.value[i] + sells + dists - buys, defl};
        }
        String density = density(c.coverage().baseDate(), c.date(c.to()));
        List<WealthPointDTO> points = new ArrayList<>();
        for (int i : sample(c, density)) {
            boolean bad = s.unavailable[i];
            points.add(new WealthPointDTO(c.date(i), bad ? null : money(s.value[i]),
                    bad ? null : money(s.value[i] * acc[i][4]), money(acc[i][0]), money(acc[i][1]),
                    money(acc[i][2]), bad ? null : money(acc[i][3])));
        }
        return new WealthDTO(c.coverage(), realBase, density, points);
    }

    // ------------------------------------------------------------------ risco (E07)

    public RiskDTO risk(UUID userId, Request req) {
        Ctx c = context(userId, req);
        if (c.empty()) {
            return new RiskDTO(c.coverage(), null, null, null, null, null, null, null, null, 0, null, null,
                    "Sem dados no período");
        }
        LocalDate from = c.date(c.from());
        LocalDate to = c.date(c.to());
        if (!c.coverage().available()) {
            return new RiskDTO(c.coverage(), null, null, null, null, null, null, null, null, 0, from, to,
                    "Indisponível: há ativo sem preço no período");
        }
        if (ChronoUnit.DAYS.between(c.coverage().baseDate(), to) < MIN_DAYS_YEAR) {
            return new RiskDTO(c.coverage(), null, null, null, null, null, null, null, null, 0, from, to,
                    "Exige ao menos 12 meses de histórico no período");
        }
        List<Double> r = new ArrayList<>();
        List<Double> cdi = new ArrayList<>();
        List<LocalDate> dates = new ArrayList<>();
        for (int i = c.from(); i <= c.to(); i++) {
            if (!c.s().active(i) || !com.activest.domain.calendar.BusinessCalendar.isBusinessDay(c.date(i))) {
                continue;
            }
            r.add(c.r()[i]);
            cdi.add(c.cdi().r()[i]);
            dates.add(c.date(i));
        }
        RiskMetrics.Result m = RiskMetrics.of(r.stream().mapToDouble(Double::doubleValue).toArray(),
                cdi.stream().mapToDouble(Double::doubleValue).toArray(), dates);
        return new RiskDTO(c.coverage(), pct(m.volatility()), pct(m.maxDrawdown()), m.peak(), m.trough(),
                round(m.sharpe(), 2), round(m.sortino(), 2), pct(m.annualReturn()), pct(m.annualCdi()), m.days(),
                from, to, "A renda fixa avaliada pela curva não oscila como no resgate antecipado: a volatilidade "
                + "da carteira tende a ficar abaixo da percebida em condições reais de liquidez.");
    }

    // ------------------------------------------------------------------ catalogo

    public PerformanceDTOs.BenchmarkCatalogDTO benchmarks() {
        return benchmarkData.catalog();
    }

    // ------------------------------------------------------------------ formatos

    private static Double nan(double v) {
        return Double.isNaN(v) ? null : v;
    }

    /** Fracao -> pontos percentuais com 4 casas. */
    static Double pct(Double fraction) {
        return fraction == null || fraction.isNaN() || fraction.isInfinite() ? null : round(fraction * 100, 4);
    }

    static Double round(Double v, int scale) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }
}
