package com.activest.domain.goal.engine;

import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.goal.GoalProjection;
import com.activest.domain.goal.GoalType;
import com.activest.domain.goal.engine.Evaluation.*;
import com.activest.domain.goal.type.ScopeKind;
import com.activest.domain.performance.BenchmarkData;
import com.activest.domain.performance.BenchmarkSeries;
import com.activest.domain.performance.PerformanceBasis;
import com.activest.domain.performance.PortfolioTimeline;
import com.activest.domain.performance.ReturnMath;
import com.activest.domain.performance.ScopeSeries;
import com.activest.domain.strategy.dto.RebalanceDTO;
import com.activest.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

/**
 * Avalia uma meta com os fatos de hoje (docs/planos/PLANO_METAS.md, Anexo A 5–7, com os ajustes da secao 5.2): o motor
 * das metas. So le a Rentabilidade (linha do tempo, {@link ScopeSeries}, {@link ReturnMath}, {@link BenchmarkData}) —
 * nada dela e alterado (criterio M-A1). Regra sem estado; as fotografias e os avisos sao do servico.
 */
public final class GoalEvaluator {

    /** D9: cotacao, PU ou CDI da curva com mais de 3 dias uteis = defasado (so marca). */
    static final int STALE_BUSINESS_DAYS = 3;
    static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private GoalEvaluator() {
    }

    public static Evaluation evaluate(GoalDefinition d, GoalFacts f) {
        EvaluationBuilder b = new EvaluationBuilder();
        b.objective = objective(d);
        switch (d.type()) {
            case PAT_1 -> patrimony(d, f, b);
            case APO_1 -> contributionsTotal(d, f, b);
            case APO_2 -> contributionsPeriodic(d, f, b);
            case REN_1 -> income(d, f, b);
            case REN_2 -> incomeCoversExpenses(d, f, b);
            case REN_3 -> incomeOverContributions(d, f, b);
            case RNT_1 -> returnTarget(d, f, b);
            case RNT_2 -> beatIndex(d, f, b);
            case ALO_1 -> allocationItem(d, f, b);
            case ALO_2 -> followStrategy(f, d, b);
            case RIS_1 -> concentration(d, f, b);
            case RIS_2 -> drawdown(d, f, b);
            case RIS_3 -> dataQuality(d, f, b);
            case ATV_1 -> assetShares(d, f, b);
            case ATV_2 -> assetValue(d, f, b);
            case ATV_3 -> dividendsBuyShares(d, f, b);
            case LP_1 -> retirement(d, f, b);
        }
        f.sourceDates().forEach(b::source);
        return b.build();
    }

    // =================================================================== patrimonio e aportes

    private static void patrimony(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        List<GoalFacts.Holding> hs = holdings(d.scope(), f);
        quality(hs, f, b);
        double v = sumValue(hs);
        double target = d.target();
        b.indicator(v, "Patrimônio do escopo", Unit.BRL).target(target).progress(v, target)
                .remaining(Math.max(target - v, 0), "Falta");
        b.achieved = v >= target - 0.005;
        b.fig("value", "Valor atual (bruto)", v, Unit.BRL, Nature.CALCULADO, "Mesmo valor da Carteira");
        b.step("V = Σ valor atual das " + hs.size() + " posições abertas do escopo (" + d.scope().describe() + ") = "
                + money(v));
        decomposition(d, f, b, v);
        Projector p = projector(d, f, b, v, true);
        growingSituation(d, f, b, p, v, target);
    }

    /** Desde a primeira transacao do escopo: aportes liquidos, resultado de mercado e proventos (a parte). */
    private static void decomposition(GoalDefinition d, GoalFacts f, EvaluationBuilder b, double v) {
        Set<UUID> ids = scopeIds(d.scope(), f);
        double buys = 0;
        double sells = 0;
        for (GoalFacts.Flow fl : f.flows()) {
            if (ids.contains(fl.positionId())) {
                if (fl.type() == TransactionType.BUY) {
                    buys += fl.amount().doubleValue();
                } else {
                    sells += fl.amount().doubleValue();
                }
            }
        }
        double dividends = f.incomes().stream().filter(GoalFacts.Income::received)
                .filter(i -> ids.contains(i.positionId())).mapToDouble(i -> nz(i.net())).sum();
        double net = buys - sells;
        b.fig("netContributions", "Aportes (compras − resgates)", net, Unit.BRL, Nature.OBSERVADO);
        b.fig("marketResult", "Valorização (valor − aportes)", v - net, Unit.BRL, Nature.CALCULADO);
        b.fig("dividendsReceived", "Proventos recebidos (à parte)", dividends, Unit.BRL,
                Nature.OBSERVADO);
    }

    private static void contributionsTotal(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        LocalDate start = d.date("startDate") != null ? d.date("startDate") : d.createdOn();
        Set<UUID> ids = scopeIds(d.scope(), f);
        double buys = 0;
        double sells = 0;
        for (GoalFacts.Flow fl : f.flows()) {
            if (ids.contains(fl.positionId()) && !fl.date().isBefore(start) && !fl.date().isAfter(f.today())) {
                if (fl.type() == TransactionType.BUY) {
                    buys += fl.amount().doubleValue();
                } else {
                    sells += fl.amount().doubleValue();
                }
            }
        }
        double acc = buys - sells;
        double target = d.target();
        b.indicator(acc, "Aportado desde " + start.format(BR), Unit.BRL).target(target)
                .progress(acc, target).remaining(Math.max(target - acc, 0), "Falta");
        b.achieved = acc >= target - 0.005;
        b.fig("buys", "Compras", buys, Unit.BRL, Nature.OBSERVADO);
        b.fig("sells", "Vendas (o que devolveram)", sells, Unit.BRL, Nature.OBSERVADO);
        if (acc < 0) {
            b.situationNote = "Retiradas superam os aportes";
        }
        double pace = monthlyPace(f, ids, start, 12);
        b.fig("monthlyAverage", "Média mensal (meses fechados)", pace, Unit.BRL, Nature.CALCULADO);
        b.step("Aporte = Σ compras − Σ vendas (líquido onde existe) de " + start.format(BR) + " a hoje = "
                + money(buys) + " − " + money(sells) + " = " + money(acc));
        linearSituation(d, f, b, acc, target, pace, "R$");
    }

    private static void contributionsPeriodic(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        boolean yearly = "YEAR".equals(d.text("periodicity"));
        LocalDate start = d.date("startDate") != null ? d.date("startDate") : d.createdOn();
        LocalDate end = d.date("endDate");
        double value = d.target();
        double tol = d.number("tolerance") != null ? d.number("tolerance") : 0;
        Integer streakGoal = d.integer("streak");
        Set<UUID> ids = scopeIds(d.scope(), f);
        LocalDate pStart = yearly ? start.withDayOfYear(1) : start.withDayOfMonth(1);
        int done = 0;
        int total = 0;
        int streak = 0;
        int best = 0;
        Boolean lastClosed = null;
        double current = 0;
        LocalDate limit = end != null && end.isBefore(f.today()) ? end : f.today();
        for (LocalDate p = pStart; !p.isAfter(limit); p = yearly ? p.plusYears(1) : p.plusMonths(1)) {
            LocalDate pEnd = (yearly ? p.plusYears(1) : p.plusMonths(1)).minusDays(1);
            double amount = netFlows(f, ids, p.isBefore(start) ? start : p, pEnd);
            boolean closed = pEnd.isBefore(f.today());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("period", yearly ? String.valueOf(p.getYear()) : p.format(MONTH));
            row.put("amount", EvaluationBuilder.round(amount, Unit.BRL));
            row.put("closed", closed);
            if (closed) {
                boolean ok = amount >= value - tol - 0.005;
                row.put("fulfilled", ok);
                total++;
                if (ok) {
                    done++;
                    streak++;
                    best = Math.max(best, streak);
                } else {
                    streak = 0;
                }
                lastClosed = ok;
            } else {
                current = amount;
                row.put("fulfilled", amount >= value - tol - 0.005);
            }
            b.items.add(row);
        }
        b.indicator((double) streak, "Sequência atual de períodos cumpridos", Unit.COUNT);
        b.fig("fulfilledPct", "Períodos cumpridos", total > 0 ? 100.0 * done / total : null, Unit.PERCENT,
                Nature.CALCULADO);
        b.fig("bestStreak", "Maior sequência", (double) best, Unit.COUNT, Nature.CALCULADO);
        b.fig("currentPeriod", "Aporte no período corrente (em andamento)", current, Unit.BRL, Nature.OBSERVADO);
        b.fig("currentRemaining", "Falta no período corrente", Math.max(value - tol - current, 0), Unit.BRL,
                Nature.CALCULADO);
        b.step("Período cumprido: aporte líquido ≥ " + money(value) + (tol > 0 ? " − tolerância " + money(tol) : ""));
        if (streakGoal != null) {
            b.target((double) streakGoal).progress((double) streak, (double) streakGoal)
                    .remaining((double) Math.max(streakGoal - streak, 0), "Faltam períodos");
            b.achieved = streak >= streakGoal;
        } else {
            // sem sequência-alvo, o alvo é o valor do aporte: o indicador comparado é o aporte do período corrente
            // (revisão de 25/10/2026 — antes a sequência, em períodos, aparecia contra o alvo em reais)
            b.indicator(current, "Aporte no período corrente (" + (yearly ? "ano" : "mês") + ")", Unit.BRL)
                    .target(value).progress(current, Math.max(value - tol, 0.01))
                    .remaining(Math.max(value - tol - current, 0), "Falta no período");
            b.fig("streak", "Sequência atual de períodos cumpridos", (double) streak, Unit.COUNT, Nature.CALCULADO);
            b.achieved = end != null && end.isBefore(f.today()) && total > 0 && done == total;
        }
        if (b.achieved) {
            b.situation = GoalSituation.ATINGIDA;
        } else if (end != null && end.isBefore(f.today())) {
            b.situation = GoalSituation.PRAZO_VENCIDO;
        } else if (lastClosed == null) {
            b.situation = GoalSituation.EM_ANDAMENTO;
            b.situationNote = "Ainda não há período fechado";
        } else {
            b.situation = lastClosed ? GoalSituation.NO_RITMO : GoalSituation.ATRASADA;
        }
    }

    // =================================================================== renda passiva

    /** Janela de meses fechados (D10): os {@code k} meses civis antes do mes corrente. */
    record Window(LocalDate from, LocalDate to, int months, int history) {
    }

    private static Window window(GoalFacts f, Set<UUID> ids, int k) {
        LocalDate firstOfMonth = f.today().withDayOfMonth(1);
        LocalDate from = firstOfMonth.minusMonths(k);
        LocalDate to = firstOfMonth.minusDays(1);
        LocalDate first = f.flows().stream().filter(fl -> ids.contains(fl.positionId())).map(GoalFacts.Flow::date)
                .min(Comparator.naturalOrder()).orElse(null);
        int history = first == null ? 0
                : (int) Math.max(0, Math.min(k, ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(f.today()))));
        return new Window(from, to, k, history);
    }

    private static double receivedIncome(GoalFacts f, Set<UUID> ids, LocalDate from, LocalDate to) {
        return f.incomes().stream().filter(GoalFacts.Income::received).filter(GoalFacts.Income::isIncome)
                .filter(i -> ids.contains(i.positionId()))
                .filter(i -> i.refDate() != null && !i.refDate().isBefore(from) && !i.refDate().isAfter(to))
                .mapToDouble(i -> nz(i.net())).sum();
    }

    /** Media mensal da renda liquida na janela (soma ÷ janela inteira, D10) e os numeros que a acompanham. */
    private static double incomeFigures(GoalDefinition d, GoalFacts f, EvaluationBuilder b, Set<UUID> ids, int k) {
        Window w = window(f, ids, k);
        double sum = receivedIncome(f, ids, w.from(), w.to());
        double avg = sum / k;
        b.fig("windowSum", "Proventos líquidos na janela (" + k + " meses fechados)", sum, Unit.BRL, Nature.OBSERVADO);
        b.fig("monthlyAverage", "Média mensal", avg, Unit.BRL, Nature.CALCULADO,
                w.history() < k ? w.history() + " meses de histórico (soma ÷ " + k + ")" : null);
        double month = receivedIncome(f, ids, f.today().withDayOfMonth(1), f.today());
        b.fig("currentMonth", "Mês corrente (parcial)", month, Unit.BRL, Nature.OBSERVADO);
        double toReceive = f.incomes().stream().filter(i -> !i.received()).filter(GoalFacts.Income::isIncome)
                .filter(i -> ids.contains(i.positionId())).mapToDouble(i -> nz(i.net())).sum();
        b.fig("toReceive", "A receber (anunciado, fora das somas)", toReceive, Unit.BRL, Nature.OBSERVADO);
        long noDate = f.incomes().stream().filter(i -> !i.received() && i.paymentDate() == null)
                .filter(i -> ids.contains(i.positionId())).count();
        if (noDate > 0) {
            b.partial(noDate + " provento(s) anunciado(s) sem data de pagamento");
        }
        double rolling = f.incomes().stream().filter(GoalFacts.Income::received)
                .filter(i -> ids.contains(i.positionId()))
                .filter(i -> i.refDate() != null && i.refDate().isAfter(f.today().minusMonths(12)))
                .mapToDouble(i -> nz(i.net())).sum();
        b.fig("rolling12m", "Proventos dos últimos 12 meses (como no Dashboard)", rolling, Unit.BRL,
                Nature.OBSERVADO);
        b.step("Renda média = Σ proventos líquidos (sem amortização/restituição) de " + w.from().format(BR) + " a "
                + w.to().format(BR) + " ÷ " + k + " = " + money(sum) + " ÷ " + k + " = " + money(avg));
        if (w.history() == 0 && sum == 0) {
            b.partial("Escopo sem histórico de proventos");
        }
        return avg;
    }

    private static void income(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        Set<UUID> ids = scopeIds(d.scope(), f);
        quality(holdings(d.scope(), f), f, b);
        boolean yearly = "YEAR".equals(d.text("unit"));
        int k = d.integer("window") != null ? d.integer("window") : 12;
        double avg = incomeFigures(d, f, b, ids, k);
        double indicator = yearly ? receivedIncome(f, ids, f.today().withDayOfMonth(1).minusMonths(12),
                f.today().withDayOfMonth(1).minusDays(1)) : avg;
        double target = d.target();
        b.indicator(indicator, yearly ? "Renda anual (12 meses fechados)" : "Renda média mensal", Unit.BRL)
                .target(target).progress(indicator, target).remaining(Math.max(target - indicator, 0), "Falta");
        b.achieved = indicator >= target - 0.005;
        double v = sumValue(holdings(d.scope(), f));
        double yieldA = v > 0 ? avg * 12 / v : 0;
        b.fig("yield", "Rendimento em proventos (renda de 12 meses ÷ valor de hoje)", yieldA * 100, Unit.PERCENT, Nature.CALCULADO,
                "Sobre o valor de hoje do escopo");
        double monthlyTarget = yearly ? target / 12 : target;
        Projector p = projector(d, f, b, v, true);
        incomeProjection(d, f, b, p, v, yieldA, monthlyTarget);
    }

    private static void incomeCoversExpenses(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        Set<UUID> ids = scopeIds(d.scope(), f);
        quality(holdings(d.scope(), f), f, b);
        int k = d.integer("window") != null ? d.integer("window") : 12;
        double avg = incomeFigures(d, f, b, ids, k);
        double expense = d.number("expense");
        LocalDate expenseDate = d.date("expenseDate") != null ? d.date("expenseDate") : d.createdOn();
        if (d.flag("ipcaCorrection") && expenseDate.isBefore(f.today())) {
            Optional<GoalFacts.Accumulated> ipca = f.indices().accumulated("IPCA", expenseDate, f.today());
            if (ipca.isPresent()) {
                expense = expense * (1 + ipca.get().rate());
                b.source("IPCA", ipca.get().until());
                b.step("Despesa corrigida pelo IPCA de " + expenseDate.format(BR) + " até " + ipca.get().until().format(BR));
            } else {
                b.partial("IPCA indisponível para corrigir a despesa");
            }
        }
        double pctGoal = d.target() > 0 ? d.target() : 100;
        double coverage = expense > 0 ? avg / expense * 100 : 0;
        b.indicator(coverage, "Cobertura das despesas", Unit.PERCENT).target(pctGoal).progress(coverage, pctGoal);
        double needed = expense * pctGoal / 100;
        b.fig("expense", "Despesa mensal" + (d.flag("ipcaCorrection") ? " (corrigida)" : ""), expense, Unit.BRL,
                Nature.CALCULADO);
        b.fig("incomeNeeded", "Renda necessária", needed, Unit.BRL, Nature.CALCULADO);
        b.unit = Unit.PERCENT;
        b.remaining(Math.max(needed - avg, 0), "Falta de renda por mês", Unit.BRL);
        b.achieved = coverage >= pctGoal - 1e-9;
        double v = sumValue(holdings(d.scope(), f));
        double yieldA = v > 0 ? avg * 12 / v : 0;
        Projector p = projector(d, f, b, v, true);
        incomeProjection(d, f, b, p, v, yieldA, needed);
    }

    private static void incomeOverContributions(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        Set<UUID> ids = scopeIds(d.scope(), f);
        quality(holdings(d.scope(), f), f, b);
        int k = d.integer("window") != null ? d.integer("window") : 12;
        double avg = incomeFigures(d, f, b, ids, k);
        Window w = window(f, ids, k);
        double contrib = netFlows(f, ids, w.from(), w.to()) / k;
        b.indicator(avg, "Renda média mensal", Unit.BRL).target(Math.max(contrib, 0));
        b.fig("contributionAverage", "Aporte médio por mês", contrib, Unit.BRL, Nature.CALCULADO);
        if (contrib <= 0) {
            b.achieved = true;
            b.situationNote = "Sem aportes líquidos no período";
            b.progressPct = EvaluationBuilder.scale(100, 2);
            b.remaining(0.0, "Falta");
        } else {
            b.fig("ratio", "Renda ÷ aporte", avg / contrib * 100, Unit.PERCENT, Nature.CALCULADO);
            b.progress(avg, contrib).remaining(Math.max(contrib - avg, 0), "Falta de renda por mês");
            b.achieved = avg >= contrib - 0.005;
        }
        double v = sumValue(holdings(d.scope(), f));
        double yieldA = v > 0 ? avg * 12 / v : 0;
        Projector p = projector(d, f, b, v, true);
        incomeProjection(d, f, b, p, v, yieldA, Math.max(contrib, 0));
    }

    // =================================================================== rentabilidade e risco sobre a serie

    /** O recorte da meta sobre a linha do tempo da Rentabilidade (base bruta — P2). */
    private static ScopeSeries series(GoalDefinition d, GoalFacts f, PortfolioTimeline t) {
        Predicate<PortfolioTimeline.PositionSeries> pred = p -> {
            PositionInfo info = f.positions().get(p.id());
            return info != null && d.scope().includes(info);
        };
        return ScopeSeries.of(t, pred, PerformanceBasis.GROSS);
    }

    /** {from, to} no eixo da linha do tempo, como a tela Rentabilidade resolve o periodo; nulo sem dados. */
    private static int[] period(GoalDefinition d, PortfolioTimeline t, ScopeSeries s) {
        int[] span = s.span();
        if (span[0] < 0) {
            return null;
        }
        LocalDate end = "CUSTOM".equals(d.text("period")) && d.date("to") != null ? d.date("to") : t.today();
        int to = Math.min(t.indexOnOrBefore(end), t.dates().size() - 1);
        if (to < 0) {
            return null;
        }
        LocalDate last = t.dates().get(to);
        int from = switch (d.text("period") == null ? "SINCE_START" : d.text("period")) {
            case "YTD" -> t.indexOnOrAfter(LocalDate.of(last.getYear() - 1, 12, 31).plusDays(1));
            case "LAST_12M" -> t.indexOnOrAfter(last.minusYears(1).plusDays(1));
            case "CUSTOM" -> d.date("from") != null ? t.indexOnOrAfter(d.date("from")) : span[0];
            default -> span[0];
        };
        from = Math.max(from, span[0]);
        return from > to || from < 0 ? null : new int[]{from, to};
    }

    private static void returnTarget(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.PERCENT;
        PortfolioTimeline t = f.timeline().get();
        if (t.isEmpty()) {
            b.unavailable("Carteira sem histórico");
            return;
        }
        ScopeSeries s = series(d, f, t);
        int[] pr = period(d, t, s);
        if (pr == null) {
            b.unavailable("Sem dados no período");
            return;
        }
        double[] r = ReturnMath.daily(s);
        Double nominal = ReturnMath.chain(r, pr[0], pr[1]);
        b.source("Série da carteira", t.dates().get(pr[1]));
        b.fig("periodFrom", "Período: início", null, Unit.COUNT, Nature.OBSERVADO, t.dates().get(pr[0]).format(BR));
        b.fig("periodTo", "Período: fim", null, Unit.COUNT, Nature.OBSERVADO, t.dates().get(pr[1]).format(BR));
        if (nominal == null) {
            List<String> missing = new ArrayList<>(s.missingTickers(pr[0], pr[1]));
            b.unavailable("Indisponível: há ativo sem preço no período" + (missing.isEmpty() ? "" : " (" + String.join(", ", missing) + ")"));
            return;
        }
        Double value = nominal;
        String label = "Rentabilidade nominal";
        if (d.flag("real")) {
            BenchmarkSeries.Daily ipca = f.benchmarks().byCode("IPCA", t.dates(), t.today());
            double[] inf = ReturnMath.chainUntilGap(ipca.r(), pr[0], pr[1]);
            if (Double.isNaN(inf[0])) {
                b.unavailable("IPCA indisponível no período");
                return;
            }
            Double nominalUntil = ReturnMath.chain(r, pr[0], (int) inf[1]);
            value = ReturnMath.real(nominalUntil, inf[0]);
            label = "Rentabilidade real";
            b.fig("inflation", "IPCA no período", inf[0] * 100, Unit.PERCENT, Nature.OBSERVADO,
                    (int) inf[1] < pr[1] ? "até " + t.dates().get((int) inf[1]).format(BR) + " (último divulgado)" : null);
            b.source("IPCA", ipca.until());
        }
        b.fig("nominal", "Rentabilidade (sem descontar a inflação)", nominal * 100, Unit.PERCENT, Nature.CALCULADO,
                "Mesma conta da tela Rentabilidade");
        if ("ANNUALIZED".equals(d.text("basis"))) {
            long days = ChronoUnit.DAYS.between(t.dates().get(Math.max(pr[0] - 1, 0)), t.dates().get(pr[1]));
            if (days < 365) {
                b.unavailable("Período curto para anualizar (menos de 12 meses)");
                return;
            }
            int bd = BusinessCalendar.businessDaysBetween(t.dates().get(pr[0]), t.dates().get(pr[1]));
            value = ReturnMath.annualize(value, bd);
            label += " anualizada";
        }
        double target = d.target();
        b.indicator(value * 100, label, Unit.PERCENT).target(target);
        b.achieved = value * 100 >= target - 1e-9;
        b.remaining(b.achieved ? 0.0 : target - value * 100, "Falta", Unit.PP);
        b.unit = Unit.PERCENT;
        b.step("TWR diário r = (V + vendas + proventos) ÷ (V da véspera + compras) − 1, composto de "
                + t.dates().get(pr[0]).format(BR) + " a " + t.dates().get(pr[1]).format(BR));
        controlSituation(d, f, b);
    }

    private static void beatIndex(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.PP;
        PortfolioTimeline t = f.timeline().get();
        if (t.isEmpty()) {
            b.unavailable("Carteira sem histórico");
            return;
        }
        ScopeSeries s = series(d, f, t);
        int[] pr = period(d, t, s);
        if (pr == null) {
            b.unavailable("Sem dados no período");
            return;
        }
        String code = d.text("index");
        String form = d.text("form") != null ? d.text("form") : "INDEX";
        Double rate = d.number("rate");
        BenchmarkData.Ref ref = "PLUS_RATE".equals(form) ? new BenchmarkData.Ref("IPCA_MAIS", rate)
                : new BenchmarkData.Ref(code, null);
        Map<String, BenchmarkData.Resolved> res;
        try {
            res = f.benchmarks().resolve(List.of(ref), t.dates(), t.today());
        } catch (RuntimeException e) {
            b.unavailable("Índice indisponível: " + e.getMessage());
            return;
        }
        if (res.isEmpty()) {
            b.unavailable("Índice indisponível");
            return;
        }
        BenchmarkData.Resolved idx = res.values().iterator().next();
        double[] bi = ReturnMath.chainUntilGap(idx.daily().r(), pr[0], pr[1]);
        if (Double.isNaN(bi[0])) {
            b.unavailable(idx.name() + " sem dado no período");
            return;
        }
        int until = (int) bi[1];
        double[] r = ReturnMath.daily(s);
        Double rc = ReturnMath.chain(r, pr[0], until);
        if (rc == null) {
            List<String> missing = new ArrayList<>(s.missingTickers(pr[0], until));
            b.unavailable("Indisponível: há ativo sem preço no período" + (missing.isEmpty() ? "" : " (" + String.join(", ", missing) + ")"));
            return;
        }
        double ri = bi[0];
        double adj = "PERCENT_OF".equals(form) && rate != null ? ri * rate / 100 : ri;
        String idxLabel = "PERCENT_OF".equals(form) && rate != null ? fmtPct(rate) + "% do " + idx.name() : idx.name();
        double margin = d.target();
        double diffPp = (rc - adj) * 100;
        b.indicator(diffPp, "Diferença carteira − " + idxLabel, Unit.PP).target(margin);
        b.fig("portfolio", "Rentabilidade da carteira", rc * 100, Unit.PERCENT, Nature.CALCULADO);
        b.fig("index", idxLabel, adj * 100, Unit.PERCENT, Nature.OBSERVADO);
        b.fig("relative", "Diferença relativa", ((1 + rc) / (1 + adj) - 1) * 100, Unit.PERCENT, Nature.CALCULADO);
        if (ri > 1e-9) {
            b.fig("percentOfIndex", "% do " + idx.name(), rc / ri * 100, Unit.PERCENT, Nature.CALCULADO);
        }
        if (until < pr[1]) {
            b.situationNote = "Período encurtado até " + t.dates().get(until).format(BR) + " (último dado do índice)";
        }
        b.source(idx.name(), idx.daily().until());
        b.achieved = diffPp >= margin - 1e-9;
        b.remaining(b.achieved ? 0.0 : margin - diffPp, "Falta", Unit.PP);
        b.step("Carteira e índice no mesmo intervalo: " + t.dates().get(pr[0]).format(BR) + " a "
                + t.dates().get(until).format(BR) + "; diferença = " + fmtPct(rc * 100) + "% − " + fmtPct(adj * 100) + "%");
        controlSituation(d, f, b);
    }

    private static void drawdown(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.PERCENT;
        PortfolioTimeline t = f.timeline().get();
        if (t.isEmpty()) {
            b.unavailable("Carteira sem histórico");
            return;
        }
        ScopeSeries s = series(d, f, t);
        int[] span = s.span();
        if (span[0] < 0) {
            b.unavailable("Sem dados no escopo");
            return;
        }
        int to = t.dates().size() - 1;
        int from = span[0];
        if ("LAST_MONTHS".equals(d.text("window"))) {
            int months = d.integer("months") != null ? d.integer("months") : 12;
            from = Math.max(from, t.indexOnOrAfter(t.today().minusMonths(months)));
        }
        if (ChronoUnit.DAYS.between(t.dates().get(from), t.dates().get(to)) < 60) {
            b.unavailable("Série curta: menos de 2 meses");
            return;
        }
        double[] r = ReturnMath.daily(s);
        double cota = 1;
        double peak = 1;
        int peakIdx = from;
        double maxDd = 0;
        int ddPeak = from;
        int ddTrough = from;
        int curPeak = from;
        for (int i = from; i <= to; i++) {
            if (Double.isNaN(r[i])) {
                List<String> missing = new ArrayList<>(s.missingTickers(from, to));
                b.unavailable("Indisponível: há ativo sem preço na janela" + (missing.isEmpty() ? "" : " (" + String.join(", ", missing) + ")"));
                return;
            }
            cota *= 1 + r[i];
            if (cota > peak) {
                peak = cota;
                curPeak = i;
            }
            double dd = cota / peak - 1;
            if (dd < maxDd) {
                maxDd = dd;
                ddPeak = curPeak;
                ddTrough = i;
            }
        }
        peakIdx = curPeak;
        double current = cota / peak - 1;
        double limit = d.target();
        b.indicator(-current * 100, "Queda atual desde o pico", Unit.PERCENT).target(limit);
        b.fig("peakDate", "Pico atual", null, Unit.COUNT, Nature.OBSERVADO, t.dates().get(peakIdx).format(BR));
        b.fig("maxDrawdown", "Maior queda da janela", -maxDd * 100, Unit.PERCENT, Nature.CALCULADO,
                maxDd < 0 ? "de " + t.dates().get(ddPeak).format(BR) + " a " + t.dates().get(ddTrough).format(BR) : null);
        b.achieved = -current * 100 <= limit + 1e-9;
        b.remaining(b.achieved ? limit + current * 100 : -current * 100 - limit, b.achieved ? "Folga" : "Excesso", Unit.PP);
        b.step("Cota = Π(1 + r diário) desde " + t.dates().get(from).format(BR) + "; queda = cota ÷ pico − 1 "
                + "(sobre a cota, não sobre o patrimônio: resgate não é perda)");
        controlSituation(d, f, b);
    }

    // =================================================================== alocacao e concentracao

    private static List<GoalFacts.Holding> base(GoalDefinition d, GoalFacts f) {
        GoalScope.Reservation r = d.scope().reservation() != null ? d.scope().reservation() : GoalScope.Reservation.WITHOUT;
        return f.holdings().stream().filter(h -> GoalScope.all(r).includes(h.info())).toList();
    }

    private static Map<String, double[]> byDimension(List<GoalFacts.Holding> hs, ScopeKind dim) {
        Map<String, double[]> out = new LinkedHashMap<>();
        for (GoalFacts.Holding h : hs) {
            String label = h.info().label(dim);
            out.computeIfAbsent(label, k -> new double[1])[0] += h.currentValue().doubleValue();
        }
        return out;
    }

    private static void allocationItem(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.PERCENT;
        List<GoalFacts.Holding> hs = base(d, f);
        quality(hs, f, b);
        double total = sumValue(hs);
        if (total <= 0.005) {
            b.unavailable("Carteira sem valor na base");
            return;
        }
        ScopeKind dim = ScopeKind.valueOf(d.text("dimension"));
        String item = d.text("item");
        double c = 0;
        for (GoalFacts.Holding h : hs) {
            String key = h.info().key(dim);
            String label = h.info().label(dim);
            if ((key != null && key.equalsIgnoreCase(item)) || (label != null && label.equalsIgnoreCase(item))) {
                c += h.currentValue().doubleValue();
            }
        }
        double w = c / total;
        String rule = d.text("rule");
        Double min = d.number("min") != null ? d.number("min") / 100 : null;
        Double max = d.number("max") != null ? d.number("max") / 100 : null;
        b.indicator(w * 100, "Participação de " + item, Unit.PERCENT);
        b.target("MAX".equals(rule) ? max * 100 : min * 100);
        b.fig("itemValue", "Valor do item", c, Unit.BRL, Nature.CALCULADO);
        b.fig("baseValue", "Valor da base" + (d.scope().reservation() == GoalScope.Reservation.WITH ? "" : " (sem a reserva)"),
                total, Unit.BRL, Nature.CALCULADO);
        boolean okMin = min == null || w >= min - 1e-9;
        boolean okMax = max == null || w <= max + 1e-9;
        if (min != null && !okMin) {
            b.fig("toMin", "Aportar no item para chegar ao mínimo (informativo)", (min * total - c) / (1 - min),
                    Unit.BRL, Nature.CALCULADO, "Não é ordem");
            b.remaining((min - w) * 100, "Falta", Unit.PP);
        }
        if (max != null && !okMax) {
            b.fig("outsideToMax", "Aportar fora do item para voltar ao máximo (informativo)", c / max - total,
                    Unit.BRL, Nature.CALCULADO, "Não é ordem");
            b.fig("reduceToMax", "Ou reduzir o item (informativo)", (c - max * total) / (1 - max), Unit.BRL,
                    Nature.CALCULADO, "Não é ordem");
            b.remaining((w - max) * 100, "Excesso", Unit.PP);
        }
        if (okMin && okMax) {
            double slack = Math.min(min != null ? w - min : 1, max != null ? max - w : 1);
            b.remaining(slack * 100, "Folga", Unit.PP);
        }
        b.achieved = okMin && okMax;
        for (Map.Entry<String, double[]> e : byDimension(hs, dim).entrySet()) {
            b.items.add(Map.of("item", e.getKey(), "value", EvaluationBuilder.round(e.getValue()[0], Unit.BRL),
                    "share", EvaluationBuilder.round(e.getValue()[0] / total * 100, Unit.PERCENT)));
        }
        b.step("Participação = valor do item ÷ valor da base = " + money(c) + " ÷ " + money(total));
        controlSituation(d, f, b);
    }

    private static void followStrategy(GoalFacts f, GoalDefinition d, EvaluationBuilder b) {
        b.unit = Unit.PP;
        RebalanceDTO reb = f.rebalance().get();
        List<RebalanceDTO.RebalanceItem> items = reb.items() == null ? List.of() : reb.items();
        boolean hasIdeal = items.stream().anyMatch(i -> i.idealPercentage() != null && i.idealPercentage().signum() > 0);
        if (!hasIdeal) {
            b.unavailable("Sem estratégia cadastrada (defina o ideal por tipo na Estratégia)");
            return;
        }
        double tol = d.targetValue() != null ? d.target() : 5;
        double maxDev = 0;
        double sumAbs = 0;
        int outside = 0;
        for (RebalanceDTO.RebalanceItem i : items) {
            double cur = i.currentPercentage() != null ? i.currentPercentage().doubleValue() : 0;
            double ideal = i.idealPercentage() != null ? i.idealPercentage().doubleValue() : 0;
            double dev = cur - ideal;
            boolean out = Math.abs(dev) > tol + 1e-9;
            if (out) {
                outside++;
            }
            maxDev = Math.max(maxDev, Math.abs(dev));
            sumAbs += Math.abs(dev);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", i.typeName());
            row.put("current", EvaluationBuilder.round(cur, Unit.PERCENT));
            row.put("ideal", EvaluationBuilder.round(ideal, Unit.PERCENT));
            row.put("deviation", EvaluationBuilder.round(dev, Unit.PP));
            row.put("outside", out);
            b.items.add(row);
        }
        b.indicator(maxDev, "Maior desvio da estratégia", Unit.PP).target(tol);
        b.fig("distance", "Parte da carteira fora do lugar", sumAbs / 2, Unit.PP, Nature.CALCULADO);
        b.fig("outside", "Tipos fora da tolerância", (double) outside, Unit.COUNT, Nature.CALCULADO);
        b.fig("base", "Base (sem a reserva de emergência)", reb.totalPortfolioValue() != null
                ? reb.totalPortfolioValue().doubleValue() : null, Unit.BRL, Nature.CALCULADO, "Mesmo número do Rebalanceamento");
        b.achieved = outside == 0;
        b.remaining(b.achieved ? tol - maxDev : maxDev - tol, b.achieved ? "Folga" : "Excesso", Unit.PP);
        b.step("% atual e ideal por tipo do Rebalanceamento; fora = |atual − ideal| > " + fmtPct(tol) + " p.p.");
        controlSituation(d, f, b);
    }

    private static void concentration(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.PERCENT;
        List<GoalFacts.Holding> hs = base(d, f);
        quality(hs, f, b);
        double total = sumValue(hs);
        if (total <= 0.005) {
            b.unavailable("Carteira sem valor na base");
            return;
        }
        ScopeKind dim = ScopeKind.valueOf(d.text("dimension"));
        double p = d.target() / 100;
        String topName = null;
        double top = 0;
        List<Map.Entry<String, double[]>> entries = new ArrayList<>(byDimension(hs, dim).entrySet());
        entries.sort((x, y) -> Double.compare(y.getValue()[0], x.getValue()[0]));
        int above = 0;
        for (Map.Entry<String, double[]> e : entries) {
            double c = e.getValue()[0];
            double w = c / total;
            if (w > top) {
                top = w;
                topName = e.getKey();
            }
            boolean over = w > p + 1e-9;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item", e.getKey());
            row.put("value", EvaluationBuilder.round(c, Unit.BRL));
            row.put("share", EvaluationBuilder.round(w * 100, Unit.PERCENT));
            row.put("above", over);
            if (over) {
                above++;
                row.put("excessPp", EvaluationBuilder.round((w - p) * 100, Unit.PP));
                row.put("reduce", EvaluationBuilder.round((c - p * total) / (1 - p), Unit.BRL));
            }
            b.items.add(row);
        }
        b.indicator(top * 100, "Maior participação" + (topName != null ? " (" + topName + ")" : ""), Unit.PERCENT)
                .target(d.target());
        b.fig("above", "Itens acima do limite", (double) above, Unit.COUNT, Nature.CALCULADO);
        b.achieved = above == 0;
        b.remaining(b.achieved ? d.target() - top * 100 : top * 100 - d.target(), b.achieved ? "Folga" : "Excesso", Unit.PP);
        b.step("Participação de cada item = valor ÷ base (" + money(total) + "); o limite exato está dentro");
        controlSituation(d, f, b);
    }

    private static void dataQuality(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.COUNT;
        @SuppressWarnings("unchecked")
        List<String> kinds = d.params().get("kinds") instanceof List<?> l ? (List<String>) l
                : List.of("STALE_PRICE", "NO_PRICE", "NO_CURVE", "BONUS_NO_COST", "DIVIDEND_NO_DATE", "IMPORT_PENDING");
        double total = sumValue(f.holdings());
        int count = 0;
        for (GoalFacts.Holding h : f.holdings()) {
            List<String> found = new ArrayList<>();
            if (kinds.contains("NO_PRICE") && h.noPrice()) {
                found.add("NO_PRICE");
            }
            if (kinds.contains("NO_CURVE") && h.noCurve()) {
                found.add("NO_CURVE");
            }
            if (kinds.contains("STALE_PRICE") && !h.noPrice() && stale(h, f.today())) {
                found.add("STALE_PRICE");
            }
            if (kinds.contains("BONUS_NO_COST") && h.pendingBonus() > 0) {
                found.add("BONUS_NO_COST");
            }
            for (String k : found) {
                count++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("issue", k);
                row.put("item", h.info().ticker());
                row.put("date", h.priceDate());
                row.put("weight", total > 0 ? EvaluationBuilder.round(h.currentValue().doubleValue() / total * 100, Unit.PERCENT) : null);
                b.items.add(row);
            }
        }
        if (kinds.contains("DIVIDEND_NO_DATE")) {
            for (GoalFacts.Income i : f.incomes()) {
                if (!i.received() && i.paymentDate() == null) {
                    count++;
                    PositionInfo info = f.positions().get(i.positionId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("issue", "DIVIDEND_NO_DATE");
                    row.put("item", info != null ? info.ticker() : null);
                    row.put("date", i.exDate());
                    b.items.add(row);
                }
            }
        }
        if (kinds.contains("IMPORT_PENDING") && f.pendingImports() > 0) {
            count += f.pendingImports();
            b.items.add(Map.of("issue", "IMPORT_PENDING", "item", "Integrações", "count", f.pendingImports()));
        }
        double max = d.targetValue() != null ? d.target() : 0;
        b.indicator((double) count, "Pendências de dados", Unit.COUNT).target(max);
        b.achieved = count <= max;
        b.remaining((double) Math.abs(count - (int) max), b.achieved ? "Folga" : "Excesso");
        b.step("Conta as pendências escolhidas nas posições abertas, nos proventos a receber e nas integrações");
        controlSituation(d, f, b);
    }

    // =================================================================== por ativo

    // O ativo da meta e o id (V13: o ticker se repete entre tipos); d.text("ticker") fica so como rotulo
    private static List<GoalFacts.Holding> ofAsset(GoalDefinition d, GoalFacts f) {
        Long id = d.assetId();
        return f.holdings().stream().filter(h -> id != null && id.equals(h.info().assetId())).toList();
    }

    private static Set<UUID> idsOfAsset(GoalDefinition d, GoalFacts f) {
        Long id = d.assetId();
        Set<UUID> ids = new HashSet<>();
        f.positions().values().stream().filter(p -> id != null && id.equals(p.assetId())).forEach(p -> ids.add(p.id()));
        return ids;
    }

    private static Double priceOf(GoalDefinition d, GoalFacts f, List<GoalFacts.Holding> hs) {
        for (GoalFacts.Holding h : hs) {
            if (h.currentPrice() != null && h.currentPrice().signum() > 0) {
                return h.currentPrice().doubleValue();
            }
        }
        BigDecimal p = f.catalogPrice().apply(d.assetId());
        return p != null && p.signum() > 0 ? p.doubleValue() : null;
    }

    private static void assetShares(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.SHARES;
        List<GoalFacts.Holding> hs = ofAsset(d, f);
        if (hs.stream().anyMatch(h -> "RENDA_FIXA".equals(h.info().typeName()))) {
            b.unavailable("Renda fixa privada não tem cotas");
            return;
        }
        quality(hs, f, b);
        double shares = hs.stream().mapToDouble(h -> h.shares() != null ? h.shares().doubleValue() : 0).sum();
        double target = d.target();
        b.indicator(shares, "Cotas de " + d.text("ticker"), Unit.SHARES).target(target).progress(shares, target)
                .remaining(Math.max(target - shares, 0), "Faltam cotas");
        b.achieved = shares >= target - 1e-9;
        Double price = priceOf(d, f, hs);
        if (price != null && !b.achieved) {
            b.fig("costOfRemaining", "Custo estimado da falta ao preço atual", (target - shares) * price, Unit.BRL,
                    Nature.ESTIMATIVA);
        }
        Set<UUID> ids = idsOfAsset(d, f);
        LocalDate to = f.today().withDayOfMonth(1).minusDays(1);
        LocalDate from = f.today().withDayOfMonth(1).minusMonths(12);
        double bought = 0;
        for (GoalFacts.Flow fl : f.flows()) {
            if (ids.contains(fl.positionId()) && !fl.date().isBefore(from) && !fl.date().isAfter(to) && fl.shares() != null) {
                bought += fl.type() == TransactionType.BUY ? fl.shares().doubleValue() : -fl.shares().doubleValue();
            }
        }
        double pace = bought / 12;
        b.fig("pace", "Ritmo de compra (cotas/mês, 12 meses fechados)", pace, Unit.SHARES, Nature.CALCULADO);
        b.step("Cotas = as da Carteira (com desdobramento, grupamento e bonificação) = " + fmtNum(shares));
        linearSituation(d, f, b, shares, target, pace, "cotas");
    }

    private static void assetValue(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        List<GoalFacts.Holding> hs = ofAsset(d, f);
        quality(hs, f, b);
        boolean cost = "COST".equals(d.text("basis"));
        double v = hs.stream().mapToDouble(h -> (cost ? h.cost() : h.currentValue()).doubleValue()).sum();
        double target = d.target();
        b.indicator(v, (cost ? "Valor aplicado em " : "Valor de mercado de ") + d.text("ticker"), Unit.BRL)
                .target(target).progress(v, target).remaining(Math.max(target - v, 0), "Falta");
        b.achieved = v >= target - 0.005;
        b.step((cost ? "Custo" : "Valor atual") + " das posições de " + d.text("ticker") + " = " + money(v));
        Projector p = projector(d, f, b, v, !cost);
        growingSituation(d, f, b, p, v, target);
    }

    private static void dividendsBuyShares(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        b.unit = Unit.SHARES;
        List<GoalFacts.Holding> hs = ofAsset(d, f);
        quality(hs, f, b);
        Set<UUID> ids = idsOfAsset(d, f);
        LocalDate to = f.today().withDayOfMonth(1).minusDays(1);
        LocalDate from = f.today().withDayOfMonth(1).minusMonths(12);
        Set<String> seen = new HashSet<>();
        double perShare = 0;
        for (GoalFacts.Income i : f.incomes()) {
            if (ids.contains(i.positionId()) && i.received() && i.isIncome() && i.netPerShare() != null
                    && i.refDate() != null && !i.refDate().isBefore(from) && !i.refDate().isAfter(to)
                    && seen.add(i.exDate() + "|" + i.type() + "|" + i.netPerShare())) {
                perShare += i.netPerShare().doubleValue();
            }
        }
        double monthly = perShare / 12;
        if (monthly <= 0) {
            b.unavailable("O ativo não pagou proventos nos últimos 12 meses fechados");
            return;
        }
        Double price = priceOf(d, f, hs);
        if (price == null) {
            b.unavailable("Sem cotação para " + d.text("ticker"));
            return;
        }
        double shares = hs.stream().mapToDouble(h -> h.shares() != null ? h.shares().doubleValue() : 0).sum();
        double target = d.targetValue() != null ? d.target() : 1;
        double buyable = monthly * shares / price;
        double needed = target * price / monthly;
        b.indicator(buyable, "Cotas compráveis por mês com os proventos", Unit.SHARES).target(target)
                .progress(shares, needed).remaining(Math.max(needed - shares, 0), "Cotas a mais na carteira para chegar lá");
        b.fig("monthlyPerShare", "Provento médio mensal por cota", monthly, Unit.BRL, Nature.CALCULADO);
        b.fig("price", "Preço atual", price, Unit.BRL, Nature.OBSERVADO);
        b.fig("shares", "Cotas atuais", shares, Unit.SHARES, Nature.OBSERVADO);
        b.fig("sharesNeeded", "Cotas necessárias", needed, Unit.SHARES, Nature.CALCULADO);
        b.achieved = buyable >= target - 1e-9;
        b.step("Cotas compráveis = provento mensal por cota × cotas ÷ preço = " + fmtNum(monthly) + " × "
                + fmtNum(shares) + " ÷ " + fmtNum(price));
        if (b.achieved) {
            b.situation = GoalSituation.ATINGIDA;
        } else {
            b.situation = GoalSituation.EM_ANDAMENTO;
        }
    }

    // =================================================================== longo prazo

    private static void retirement(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        List<GoalFacts.Holding> hs = holdings(d.scope(), f);
        quality(hs, f, b);
        Set<UUID> ids = scopeIds(d.scope(), f);
        double v = sumValue(hs);
        int k = 12;
        Window w = window(f, ids, k);
        double avgIncome = receivedIncome(f, ids, w.from(), w.to()) / k;
        double yieldA = v > 0 ? avgIncome * 12 / v : 0;
        int n = (int) Math.max(0, ChronoUnit.MONTHS.between(f.today(), d.targetDate()));
        b.indicator(v, "Patrimônio de hoje", Unit.BRL);
        Projector p = projector(d, f, b, v, true);
        b.situation = GoalSituation.ESTIMATIVA;
        if (p.suspended != null) {
            b.projection = new Projection(p.assumptions, List.of(), p.suspended, List.of());
            return;
        }
        double fv = GoalProjection.futureValue(v, p.pmt, p.i, n);
        double real = GoalProjection.real(fv, p.inflation, n);
        double wr = d.number("withdrawalRate") != null ? d.number("withdrawalRate") / 100 : 0;
        double incomeEst = fv * (yieldA + wr) / 12;
        double incomeReal = GoalProjection.real(incomeEst, p.inflation, n);
        List<Figure> figs = new ArrayList<>();
        figs.add(new Figure("fvNominal", "Patrimônio na data (nominal)", EvaluationBuilder.estimate(fv, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
        figs.add(new Figure("fvReal", "Patrimônio na data (valores de hoje)", EvaluationBuilder.estimate(real, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
        figs.add(new Figure("incomeNominal", "Renda mensal estimada (nominal)", EvaluationBuilder.estimate(incomeEst, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA,
                wr > 0 ? "Proventos + retirada de " + fmtPct(wr * 100) + "% a.a." : "Só proventos (yield de " + fmtPct(yieldA * 100) + "% a.a.)"));
        figs.add(new Figure("incomeReal", "Renda mensal estimada (valores de hoje)", EvaluationBuilder.estimate(incomeReal, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
        if (d.targetValue() != null) {
            double desired = d.target();
            // o alvo e renda mensal: o indicador comparado com ele e a renda estimada (valores de hoje), nao o
            // patrimonio (revisao de 25/10/2026 — antes a barra media patrimonio contra renda)
            b.indicator(incomeReal, "Renda mensal estimada (valores de hoje)", Unit.BRL).target(desired)
                    .progress(incomeReal, desired);
            b.fig("wealthToday", "Patrimônio de hoje", v, Unit.BRL, Nature.OBSERVADO);
            double desiredNominal = desired * Math.pow(1 + p.inflation, n / 12.0);
            double rate = yieldA + wr;
            if (rate > 0) {
                double neededFv = desiredNominal * 12 / rate;
                Double pmt = GoalProjection.requiredPayment(neededFv, v, p.i, n);
                figs.add(new Figure("neededWealth", "Patrimônio necessário para a renda desejada", EvaluationBuilder.estimate(neededFv, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
                figs.add(new Figure("requiredPayment", "Aporte mensal necessário", EvaluationBuilder.estimate(pmt, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
            }
            b.fig("gap", "Diferença para a renda desejada (valores de hoje)", incomeReal - desired, Unit.BRL, Nature.ESTIMATIVA);
            b.situation = incomeReal >= desired ? GoalSituation.NO_RITMO : GoalSituation.ATRASADA;
            b.remaining(Math.max(desired - incomeReal, 0), "Falta de renda estimada");
        }
        b.projection = new Projection(p.assumptions, figs, null, path(v, p, n));
        b.step("Valor no prazo = PV(1+i)^n + PMT((1+i)^n − 1)/i com n = " + n + " meses");
    }

    // =================================================================== projecao (Anexo A 6)

    /** Premissas resolvidas (automaticas ou informadas) e o motivo de suspensao. */
    static final class Projector {
        double i;
        double pmt;
        double inflation;
        double annual;
        String suspended;
        final List<Assumption> assumptions = new ArrayList<>();
    }

    /**
     * Os valores automaticos das premissas (Anexo A 6.1) para o formulario mostrar antes de o cadastro estar completo
     * (revisao de 25/10/2026): o que valeria com os campos vazios — os informados sao ignorados aqui.
     */
    public static List<Assumption> automaticAssumptions(GoalDefinition d, GoalFacts f) {
        Map<String, Object> params = new java.util.LinkedHashMap<>(d.params());
        params.remove("returnRate");
        params.remove("monthlyContribution");
        params.remove("inflation");
        GoalDefinition auto = new GoalDefinition(d.type(), d.name(), d.targetValue(), d.targetDate(), d.scope(), params,
                d.createdOn());
        boolean withReturn = !(d.type() == GoalType.ATV_2 && "COST".equals(d.text("basis")));
        return resolve(auto, f, withReturn).assumptions;
    }

    private static Projector projector(GoalDefinition d, GoalFacts f, EvaluationBuilder b, double v, boolean withReturn) {
        if (!d.type().projection()) {
            return new Projector();
        }
        return resolve(d, f, withReturn);
    }

    private static Projector resolve(GoalDefinition d, GoalFacts f, boolean withReturn) {
        Projector p = new Projector();
        Set<UUID> ids = d.type() == GoalType.ATV_2 || d.type() == GoalType.ATV_1 ? idsOfAsset(d, f) : scopeIds(d.scope(), f);
        // retorno
        Double informed = d.number("returnRate");
        if (!withReturn) {
            p.annual = 0;
            p.assumptions.add(new Assumption("returnRate", "Retorno", BigDecimal.ZERO, Unit.PERCENT, "AUTOMATICA",
                    "Sem rendimento: meta pelo custo", null));
        } else if (informed != null) {
            p.annual = informed / 100;
            p.assumptions.add(new Assumption("returnRate", "Retorno nominal a.a.", EvaluationBuilder.round(informed, Unit.PERCENT),
                    Unit.PERCENT, "INFORMADA", "Fixado na meta", null));
        } else if ("PORTFOLIO".equals(d.text("returnSource")) && portfolioReturn(d, f, p)) {
            // preenchido por portfolioReturn
        } else {
            Optional<GoalFacts.Accumulated> cdi = f.indices().accumulated("CDI", f.today().minusYears(1), f.today());
            if (cdi.isEmpty()) {
                p.suspended = "Sem CDI de 12 meses para a premissa de retorno";
            } else {
                p.annual = cdi.get().rate();
                p.assumptions.add(new Assumption("returnRate", "Retorno nominal a.a.",
                        EvaluationBuilder.round(cdi.get().rate() * 100, Unit.PERCENT), Unit.PERCENT, "AUTOMATICA",
                        "CDI acumulado em 12 meses", cdi.get().until()));
            }
        }
        p.i = GoalProjection.monthlyRate(p.annual);
        // aporte
        Double pmt = d.number("monthlyContribution");
        if (pmt != null) {
            p.pmt = pmt;
            p.assumptions.add(new Assumption("monthlyContribution", "Aporte mensal", EvaluationBuilder.round(pmt, Unit.BRL),
                    Unit.BRL, "INFORMADA", "Fixado na meta", null));
        } else {
            LocalDate to = f.today().withDayOfMonth(1).minusDays(1);
            LocalDate from = f.today().withDayOfMonth(1).minusMonths(12);
            Window w = window(f, ids, 12);
            int months = Math.max(1, Math.min(12, w.history()));
            p.pmt = netFlows(f, ids, from, to) / months;
            p.assumptions.add(new Assumption("monthlyContribution", "Aporte mensal", EvaluationBuilder.round(p.pmt, Unit.BRL),
                    Unit.BRL, "AUTOMATICA", w.history() < 12 ? "Média dos " + months + " meses com posição"
                    : "Média dos 12 meses fechados", to));
        }
        // inflacao
        Double inf = d.number("inflation");
        if (inf != null) {
            p.inflation = inf / 100;
            p.assumptions.add(new Assumption("inflation", "Inflação a.a.", EvaluationBuilder.round(inf, Unit.PERCENT),
                    Unit.PERCENT, "INFORMADA", "Fixado na meta", null));
        } else {
            Optional<GoalFacts.Accumulated> probe = f.indices().accumulated("IPCA", f.today().minusMonths(14).withDayOfMonth(1), f.today());
            Optional<GoalFacts.Accumulated> ipca = probe.flatMap(a -> f.indices().accumulated("IPCA",
                    a.until().withDayOfMonth(1).minusMonths(11), a.until()));
            if (ipca.isPresent()) {
                p.inflation = ipca.get().rate();
                p.assumptions.add(new Assumption("inflation", "Inflação a.a.",
                        EvaluationBuilder.round(ipca.get().rate() * 100, Unit.PERCENT), Unit.PERCENT, "AUTOMATICA",
                        "IPCA acumulado em 12 meses (último divulgado)", ipca.get().until()));
            } else {
                p.inflation = 0;
                p.assumptions.add(new Assumption("inflation", "Inflação a.a.", BigDecimal.ZERO, Unit.PERCENT,
                        "AUTOMATICA", "IPCA indisponível: valores reais iguais aos nominais", null));
            }
        }
        return p;
    }

    /** D12 (b): TWR anualizado da carteira, so com 24 meses ou mais de historico. */
    private static boolean portfolioReturn(GoalDefinition d, GoalFacts f, Projector p) {
        PortfolioTimeline t = f.timeline().get();
        if (t.isEmpty()) {
            return false;
        }
        ScopeSeries s = series(d, f, t);
        int[] span = s.span();
        if (span[0] < 0 || ChronoUnit.MONTHS.between(t.dates().get(span[0]), t.dates().get(span[1])) < 24) {
            return false;
        }
        Double twr = ReturnMath.chain(ReturnMath.daily(s), span[0], span[1]);
        if (twr == null) {
            return false;
        }
        Double a = ReturnMath.annualize(twr, BusinessCalendar.businessDaysBetween(t.dates().get(span[0]), t.dates().get(span[1])));
        if (a == null) {
            return false;
        }
        p.annual = a;
        p.assumptions.add(new Assumption("returnRate", "Retorno nominal a.a.", EvaluationBuilder.round(a * 100, Unit.PERCENT),
                Unit.PERCENT, "AUTOMATICA", "Rentabilidade da carteira (TWR anualizado)", t.dates().get(span[1])));
        return true;
    }

    /** Crescente (Anexo A 7.1, D13, D14 a): atingida, prazo vencido, no ritmo ou atrasada pela projecao. */
    private static void growingSituation(GoalDefinition d, GoalFacts f, EvaluationBuilder b, Projector p, double v,
                                         double target) {
        List<Figure> figs = new ArrayList<>();
        String suspended = p.suspended;
        if (b.quality == Quality.INDISPONIVEL) {
            return;
        }
        if (b.achieved) {
            b.situation = GoalSituation.ATINGIDA;
        }
        int n = d.targetDate() != null ? (int) ChronoUnit.MONTHS.between(f.today(), d.targetDate()) : -1;
        if (!b.achieved && suspended == null) {
            if (d.targetDate() != null && n > 0) {
                double fv = GoalProjection.futureValue(v, p.pmt, p.i, n);
                Double need = GoalProjection.requiredPayment(target, v, p.i, n);
                figs.add(new Figure("valueAtDeadline", "Valor estimado no prazo", EvaluationBuilder.estimate(fv, Unit.BRL),
                        Unit.BRL, Nature.ESTIMATIVA, null));
                figs.add(new Figure("requiredPayment", "Aporte mensal necessário até o prazo",
                        EvaluationBuilder.estimate(need, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA,
                        need != null && need == 0 ? "Não precisa de aporte com estas premissas" : null));
                b.situation = fv >= target - 0.005 ? GoalSituation.NO_RITMO : GoalSituation.ATRASADA;
            } else if (d.targetDate() == null) {
                Double months = GoalProjection.monthsToTarget(target, v, p.pmt, p.i);
                figs.add(new Figure("estimatedDate", "Data estimada", null, Unit.COUNT, Nature.ESTIMATIVA,
                        months == null ? "Não alcança com as premissas atuais"
                                : YearMonth.from(f.today()).plusMonths((long) Math.ceil(months)).format(MONTH)));
                b.situation = GoalSituation.EM_ANDAMENTO;
            }
        }
        if (!b.achieved && d.targetDate() != null && !d.targetDate().isAfter(f.today())) {
            b.situation = GoalSituation.PRAZO_VENCIDO;
        } else if (!b.achieved && b.situation == null) {
            b.situation = GoalSituation.EM_ANDAMENTO;
            if (suspended != null) {
                b.situationNote = suspended;
            }
        }
        if (d.type().projection()) {
            b.projection = new Projection(p.assumptions, figs, b.achieved ? "Meta atingida" : suspended,
                    suspended == null && !b.achieved ? path(v, p, n > 0 ? n : 120) : List.of());
        }
    }

    /** Renda: renda estimada no prazo (patrimonio projetado × yield) ou data em que a renda chega ao alvo. */
    private static void incomeProjection(GoalDefinition d, GoalFacts f, EvaluationBuilder b, Projector p, double v,
                                         double yieldA, double monthlyTarget) {
        if (b.quality == Quality.INDISPONIVEL) {
            return;
        }
        List<Figure> figs = new ArrayList<>();
        String suspended = p.suspended;
        if (suspended == null && yieldA <= 0) {
            suspended = "Sem yield de proventos no escopo para projetar a renda";
        }
        if (b.achieved) {
            b.situation = GoalSituation.ATINGIDA;
        }
        int n = d.targetDate() != null ? (int) ChronoUnit.MONTHS.between(f.today(), d.targetDate()) : -1;
        if (!b.achieved && suspended == null) {
            double neededWealth = monthlyTarget * 12 / yieldA;
            if (d.targetDate() != null && n > 0) {
                double fv = GoalProjection.futureValue(v, p.pmt, p.i, n);
                double incomeAt = fv * yieldA / 12;
                figs.add(new Figure("incomeAtDeadline", "Renda mensal estimada no prazo (nominal)",
                        EvaluationBuilder.estimate(incomeAt, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
                figs.add(new Figure("incomeAtDeadlineReal", "Renda mensal estimada no prazo (valores de hoje)",
                        EvaluationBuilder.estimate(GoalProjection.real(incomeAt, p.inflation, n), Unit.BRL), Unit.BRL,
                        Nature.ESTIMATIVA, null));
                Double need = GoalProjection.requiredPayment(neededWealth, v, p.i, n);
                figs.add(new Figure("requiredPayment", "Aporte mensal necessário até o prazo",
                        EvaluationBuilder.estimate(need, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
                b.situation = incomeAt >= monthlyTarget - 0.005 ? GoalSituation.NO_RITMO : GoalSituation.ATRASADA;
            } else if (d.targetDate() == null) {
                Double months = GoalProjection.monthsToTarget(neededWealth, v, p.pmt, p.i);
                figs.add(new Figure("estimatedDate", "Data estimada para a renda chegar ao alvo", null, Unit.COUNT,
                        Nature.ESTIMATIVA, months == null ? "Não alcança com as premissas atuais"
                        : YearMonth.from(f.today()).plusMonths((long) Math.ceil(months)).format(MONTH)));
            }
            figs.add(new Figure("neededWealth", "Patrimônio que gera a renda-alvo (yield de hoje)",
                    EvaluationBuilder.estimate(neededWealth, Unit.BRL), Unit.BRL, Nature.ESTIMATIVA, null));
        }
        if (!b.achieved && d.targetDate() != null && !d.targetDate().isAfter(f.today())) {
            b.situation = GoalSituation.PRAZO_VENCIDO;
        } else if (!b.achieved && b.situation == null) {
            b.situation = GoalSituation.EM_ANDAMENTO;
            if (suspended != null) {
                b.situationNote = suspended;
            }
        }
        // linha da projeção no gráfico (revisão de 25/10/2026): renda mensal estimada mês a mês
        List<Map<String, Object>> incomePath = List.of();
        if (!b.achieved && suspended == null) {
            Double months = GoalProjection.monthsToTarget(monthlyTarget * 12 / yieldA, v, p.pmt, p.i);
            int horizon = n > 0 ? n : months != null ? (int) Math.min(Math.ceil(months), 240) : 120;
            incomePath = path(v, p, Math.max(horizon, 1), yieldA / 12);
        }
        b.projection = new Projection(p.assumptions, figs, b.achieved ? "Meta atingida" : suspended, incomePath);
    }

    /** Meta sem rendimento (aportes, cotas): ritmo linear (D14) — o ritmo medio cobre o que falta ate o prazo? */
    private static void linearSituation(GoalDefinition d, GoalFacts f, EvaluationBuilder b, double current,
                                        double target, double pace, String unitLabel) {
        if (b.achieved) {
            b.situation = GoalSituation.ATINGIDA;
            return;
        }
        double remaining = target - current;
        if (d.targetDate() != null && !d.targetDate().isAfter(f.today())) {
            b.situation = GoalSituation.PRAZO_VENCIDO;
            return;
        }
        List<Figure> figs = new ArrayList<>();
        if (d.targetDate() != null) {
            double months = Math.max(ChronoUnit.DAYS.between(f.today(), d.targetDate()) / 30.4375, 1e-9);
            double need = remaining / months;
            figs.add(new Figure("requiredPace", "Necessário por mês até o prazo (" + unitLabel + ")",
                    unitLabel.equals("R$") ? EvaluationBuilder.round(need, Unit.BRL) : EvaluationBuilder.round(need, Unit.SHARES),
                    unitLabel.equals("R$") ? Unit.BRL : Unit.SHARES, Nature.CALCULADO, null));
            b.situation = pace >= need - 1e-9 ? GoalSituation.NO_RITMO : GoalSituation.ATRASADA;
        } else {
            b.situation = GoalSituation.EM_ANDAMENTO;
        }
        if (pace > 0) {
            long months = (long) Math.ceil(remaining / pace);
            figs.add(new Figure("estimatedDate", "Data estimada pelo ritmo", null, Unit.COUNT, Nature.ESTIMATIVA,
                    months > GoalProjection.MAX_MONTHS ? "Não alcança no ritmo atual"
                            : YearMonth.from(f.today()).plusMonths(months).format(MONTH)));
        } else {
            figs.add(new Figure("estimatedDate", "Data estimada pelo ritmo", null, Unit.COUNT, Nature.ESTIMATIVA,
                    "Sem ritmo nos últimos 12 meses"));
        }
        // linha da projeção no gráfico (revisão de 25/10/2026): o ritmo médio somado mês a mês, até o prazo ou até o alvo
        List<Map<String, Object>> linear = new ArrayList<>();
        if (pace > 0) {
            int horizon = d.targetDate() != null
                    ? (int) Math.max(1, ChronoUnit.MONTHS.between(f.today(), d.targetDate()))
                    : (int) Math.min(Math.ceil(remaining / pace), 240);
            int step = Math.max(1, horizon / 120);
            Unit u = unitLabel.equals("R$") ? Unit.BRL : Unit.SHARES;
            for (int m = 0; m <= horizon; m += step) {
                linear.add(Map.of("month", m, "value", EvaluationBuilder.round(current + pace * m, u)));
            }
        }
        b.projection = new Projection(List.of(new Assumption("pace", "Ritmo médio (12 meses fechados)",
                unitLabel.equals("R$") ? EvaluationBuilder.round(pace, Unit.BRL) : EvaluationBuilder.round(pace, Unit.SHARES),
                unitLabel.equals("R$") ? Unit.BRL : Unit.SHARES, "AUTOMATICA", "Sem juros: é dinheiro (ou cotas) novo", null)),
                figs, null, linear);
    }

    /** Controle (limite, faixa): dentro ou fora; com prazo e antes dele, fora = em andamento. */
    private static void controlSituation(GoalDefinition d, GoalFacts f, EvaluationBuilder b) {
        if (b.quality == Quality.INDISPONIVEL) {
            return;
        }
        if (b.achieved) {
            b.situation = GoalSituation.DENTRO_DO_OBJETIVO;
        } else if (d.targetDate() != null && d.targetDate().isAfter(f.today())) {
            b.situation = GoalSituation.EM_ANDAMENTO;
        } else {
            b.situation = GoalSituation.FORA_DO_OBJETIVO;
        }
    }

    /** Pontos mes a mes da projecao (grafico tracejado), no maximo 120. */
    private static List<Map<String, Object>> path(double v, Projector p, int n) {
        return path(v, p, n, 1);
    }

    /** Valor projetado mes a mes (patrimonio; com {@code factor} = yield/12, a renda mensal estimada). */
    private static List<Map<String, Object>> path(double v, Projector p, int n, double factor) {
        List<Map<String, Object>> out = new ArrayList<>();
        int step = Math.max(1, n / 120);
        for (int m = 0; m <= n; m += step) {
            out.add(Map.of("month", m, "value",
                    EvaluationBuilder.round(GoalProjection.futureValue(v, p.pmt, p.i, m) * factor, Unit.BRL)));
        }
        return out;
    }

    // =================================================================== apoio

    static List<GoalFacts.Holding> holdings(GoalScope scope, GoalFacts f) {
        return f.holdings().stream().filter(h -> scope.includes(h.info())).toList();
    }

    static Set<UUID> scopeIds(GoalScope scope, GoalFacts f) {
        Set<UUID> ids = new HashSet<>();
        for (PositionInfo p : f.positions().values()) {
            if (scope.includes(p)) {
                ids.add(p.id());
            }
        }
        return ids;
    }

    private static double sumValue(List<GoalFacts.Holding> hs) {
        return hs.stream().mapToDouble(h -> h.currentValue().doubleValue()).sum();
    }

    private static double netFlows(GoalFacts f, Set<UUID> ids, LocalDate from, LocalDate to) {
        double s = 0;
        for (GoalFacts.Flow fl : f.flows()) {
            if (ids.contains(fl.positionId()) && !fl.date().isBefore(from) && !fl.date().isAfter(to)) {
                s += fl.type() == TransactionType.BUY ? fl.amount().doubleValue() : -fl.amount().doubleValue();
            }
        }
        return s;
    }

    /** Media mensal dos aportes liquidos nos ultimos {@code k} meses fechados, a partir de {@code start}. */
    private static double monthlyPace(GoalFacts f, Set<UUID> ids, LocalDate start, int k) {
        LocalDate to = f.today().withDayOfMonth(1).minusDays(1);
        LocalDate from = f.today().withDayOfMonth(1).minusMonths(k);
        if (start.isAfter(from)) {
            from = start;
        }
        long months = Math.max(1, Math.min(k, ChronoUnit.MONTHS.between(YearMonth.from(from), YearMonth.from(f.today()))));
        return to.isBefore(from) ? 0 : netFlows(f, ids, from, to) / months;
    }

    static boolean stale(GoalFacts.Holding h, LocalDate today) {
        return h.priceDate() != null && h.priceDate().isBefore(today)
                && BusinessCalendar.businessDaysBetween(h.priceDate().plusDays(1), today) > STALE_BUSINESS_DAYS;
    }

    /** Qualidade herdada das posicoes (D8, D9, L3): so marca, nao bloqueia. */
    private static void quality(List<GoalFacts.Holding> hs, GoalFacts f, EvaluationBuilder b) {
        for (GoalFacts.Holding h : hs) {
            if (h.noPrice()) {
                b.partial(h.info().ticker() + " sem cotação: valorizado pelo custo");
            } else if (h.noCurve()) {
                b.partial(h.info().ticker() + " sem curva: valor como está gravado");
            } else if (stale(h, f.today())) {
                b.partial(h.info().ticker() + ": cotação de " + h.priceDate().format(BR));
            }
            if (h.pendingBonus() > 0) {
                b.partial(h.info().ticker() + ": bonificação sem custo informado");
            }
        }
        if (f.pendingImports() > 0) {
            b.partial("Importação de integração aguardando confirmação");
        }
    }

    /** A meta numa frase ("Ter R$ 500.000,00 até 12/2035"). */
    public static String objective(GoalDefinition d) {
        String date = d.targetDate() != null ? " até " + d.targetDate().format(MONTH) : "";
        return switch (d.type()) {
            case PAT_1 -> "Ter " + money(d.target()) + " de patrimônio" + date;
            case APO_1 -> "Aportar " + money(d.target()) + " no total" + date;
            case APO_2 -> "Aportar " + money(d.target()) + ("YEAR".equals(d.text("periodicity")) ? " por ano" : " por mês")
                    + (d.integer("streak") != null ? " por " + d.integer("streak") + " períodos seguidos" : "");
            case REN_1 -> "Receber " + money(d.target()) + ("YEAR".equals(d.text("unit")) ? " por ano" : " por mês")
                    + " de renda passiva" + date;
            case REN_2 -> "Cobrir " + fmtPct(d.targetValue() != null ? d.target() : 100) + "% das despesas de "
                    + money(d.number("expense")) + " por mês";
            case REN_3 -> "Ter renda passiva maior que os aportes";
            case RNT_1 -> "Rentabilidade " + (d.flag("real") ? "real " : "") + "de " + fmtPct(d.target()) + "%"
                    + ("ANNUALIZED".equals(d.text("basis")) ? " a.a." : "");
            case RNT_2 -> "Superar " + ("PERCENT_OF".equals(d.text("form")) ? fmtPct(d.number("rate")) + "% do " : "")
                    + d.text("index") + ("PLUS_RATE".equals(d.text("form")) ? " + " + fmtPct(d.number("rate")) + "% a.a." : "")
                    + (d.target() != 0 ? " em " + fmtPct(d.target()) + " p.p." : "");
            case ALO_1 -> d.text("item") + " " + switch (d.text("rule")) {
                case "MAX" -> "com no máximo " + fmtPct(d.number("max")) + "%";
                case "RANGE" -> "entre " + fmtPct(d.number("min")) + "% e " + fmtPct(d.number("max")) + "%";
                default -> "com no mínimo " + fmtPct(d.number("min")) + "%";
            } + " da carteira" + date;
            case ALO_2 -> "Ficar a até " + fmtPct(d.targetValue() != null ? d.target() : 5) + " p.p. da estratégia";
            case RIS_1 -> "Nenhum item acima de " + fmtPct(d.target()) + "% da carteira";
            case RIS_2 -> "Queda máxima de " + fmtPct(d.target()) + "% desde o pico";
            case RIS_3 -> "No máximo " + (d.targetValue() != null ? (int) d.target() : 0) + " pendência(s) de dados";
            case ATV_1 -> "Ter " + fmtNum(d.target()) + " cotas de " + d.text("ticker") + date;
            case ATV_2 -> "Ter " + money(d.target()) + " em " + d.text("ticker") + date;
            case ATV_3 -> "Proventos de " + d.text("ticker") + " comprarem " + fmtNum(d.targetValue() != null ? d.target() : 1)
                    + " cota(s) por mês";
            case LP_1 -> "Aposentadoria em " + d.targetDate().format(MONTH)
                    + (d.targetValue() != null ? " com renda de " + money(d.target()) + " por mês (valores de hoje)" : "");
        };
    }

    static String money(Double v) {
        if (v == null) {
            return "—";
        }
        java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        return nf.format(v).replace(' ', ' ');
    }

    static String fmtPct(Double v) {
        if (v == null) {
            return "—";
        }
        return String.format(new Locale("pt", "BR"), "%,.2f", v).replaceAll(",00$", "");
    }

    static String fmtNum(double v) {
        return String.format(new Locale("pt", "BR"), "%,.8f", v).replaceAll("0+$", "").replaceAll(",$", "");
    }

    private static double nz(BigDecimal v) {
        return v != null ? v.doubleValue() : 0;
    }

    // =================================================================== graficos (revisao de 26/10/2026)

    /** Um ponto de um grafico da meta. */
    public record ChartPoint(LocalDate date, double value) {
    }

    /** Uma linha do grafico: a primeira mede o mesmo que o indicador; as demais sao de apoio. */
    public record ChartLine(String key, String label, List<ChartPoint> points) {
    }

    /** Inicio do periodo da meta terminando no indice {@code to} (a mesma regra de period(), deslizando). */
    private static int periodFrom(GoalDefinition d, PortfolioTimeline t, int spanStart, int to) {
        LocalDate last = t.dates().get(to);
        int from = switch (d.text("period") == null ? "SINCE_START" : d.text("period")) {
            case "YTD" -> t.indexOnOrAfter(LocalDate.of(last.getYear() - 1, 12, 31).plusDays(1));
            case "LAST_12M" -> t.indexOnOrAfter(last.minusYears(1).plusDays(1));
            case "CUSTOM" -> d.date("from") != null ? t.indexOnOrAfter(d.date("from")) : spanStart;
            default -> spanStart;
        };
        return Math.max(from, spanStart);
    }

    /** {lv, lacunas}: lv[k] = produto de (1 + r) ate o dia k − 1; lacunas[k] = dias indisponiveis ate k − 1. */
    private static double[][] levels(double[] r) {
        double[] lv = new double[r.length + 1];
        double[] gaps = new double[r.length + 1];
        lv[0] = 1;
        for (int i = 0; i < r.length; i++) {
            boolean bad = Double.isNaN(r[i]);
            lv[i + 1] = lv[i] * (bad ? 1 : 1 + r[i]);
            gaps[i + 1] = gaps[i] + (bad ? 1 : 0);
        }
        return new double[][]{lv, gaps};
    }

    /** Retorno composto de {@code from} a {@code to}; nulo com dia indisponivel no meio. */
    private static Double compound(double[][] l, int from, int to) {
        if (from > to || l[1][to + 1] - l[1][from] > 0) {
            return null;
        }
        return l[0][to + 1] / l[0][from] - 1;
    }

    /** Dias do eixo entre {@code start} e {@code end}, amostrados (~260 pontos), sempre com o ultimo. */
    private static List<Integer> days(PortfolioTimeline t, int first, LocalDate start, LocalDate end) {
        int a = Math.max(first, t.indexOnOrAfter(start));
        int z = Math.min(t.dates().size() - 1, t.indexOnOrBefore(end));
        List<Integer> out = new ArrayList<>();
        if (a < 0 || z < a) {
            return out;
        }
        int step = Math.max(1, (z - a + 1) / 260);
        for (int i = a; i <= z; i += step) {
            out.add(i);
        }
        if (out.get(out.size() - 1) != z) {
            out.add(z);
        }
        return out;
    }

    /**
     * Rentabilidade da meta como estava em cada dia (o periodo da meta terminando naquele dia: 12 meses moveis, no ano
     * ou desde o inicio), real e anualizada como a meta. E o que o indicador e o alvo medem — nao o acumulado da carteira.
     */
    private static List<ChartPoint> rolling(GoalDefinition d, PortfolioTimeline t, double[] r, double[] inflation,
                                            int spanStart, LocalDate start, LocalDate end) {
        double[][] l = levels(r);
        double[][] li = inflation != null ? levels(inflation) : null;
        LocalDate fixedEnd = "CUSTOM".equals(d.text("period")) ? d.date("to") : null;
        List<ChartPoint> out = new ArrayList<>();
        for (int i : days(t, spanStart, start, fixedEnd != null && fixedEnd.isBefore(end) ? fixedEnd : end)) {
            int from = periodFrom(d, t, spanStart, i);
            Double v = compound(l, from, i);
            if (v == null) {
                continue;
            }
            if (li != null) {
                Double inf = compound(li, from, i);
                if (inf == null) {
                    continue;
                }
                v = ReturnMath.real(v, inf);
            }
            if ("ANNUALIZED".equals(d.text("basis"))) {
                if (ChronoUnit.DAYS.between(t.dates().get(Math.max(from - 1, 0)), t.dates().get(i)) < 365) {
                    continue;
                }
                v = ReturnMath.annualize(v, BusinessCalendar.businessDaysBetween(t.dates().get(from), t.dates().get(i)));
                if (v == null) {
                    continue;
                }
            }
            out.add(new ChartPoint(t.dates().get(i), v * 100));
        }
        return out;
    }

    /** RNT-1: a rentabilidade da meta dia a dia. */
    public static List<ChartLine> returnChart(GoalDefinition d, GoalFacts f, LocalDate start, LocalDate end) {
        PortfolioTimeline t = f.timeline().get();
        if (t.isEmpty()) {
            return List.of();
        }
        ScopeSeries s = series(d, f, t);
        int[] span = s.span();
        if (span[0] < 0) {
            return List.of();
        }
        double[] inflation = d.flag("real") ? f.benchmarks().byCode("IPCA", t.dates(), t.today()).r() : null;
        String label = (d.flag("real") ? "Rentabilidade real" : "Rentabilidade nominal") + periodLabel(d)
                + ("ANNUALIZED".equals(d.text("basis")) ? ", anualizada" : "");
        return List.of(new ChartLine("value", label, rolling(d, t, ReturnMath.daily(s), inflation, span[0], start, end)));
    }

    /** RNT-2: carteira e indice, cada um no periodo da meta terminando em cada dia (a diferenca e o indicador). */
    public static List<ChartLine> beatIndexChart(GoalDefinition d, GoalFacts f, LocalDate start, LocalDate end) {
        PortfolioTimeline t = f.timeline().get();
        if (t.isEmpty()) {
            return List.of();
        }
        ScopeSeries s = series(d, f, t);
        int[] span = s.span();
        if (span[0] < 0) {
            return List.of();
        }
        String form = d.text("form") != null ? d.text("form") : "INDEX";
        Double rate = d.number("rate");
        BenchmarkData.Ref ref = "PLUS_RATE".equals(form) ? new BenchmarkData.Ref("IPCA_MAIS", rate)
                : new BenchmarkData.Ref(d.text("index"), null);
        List<ChartLine> out = new ArrayList<>();
        out.add(new ChartLine("value", "Carteira" + periodLabel(d),
                rolling(d, t, ReturnMath.daily(s), null, span[0], start, end)));
        try {
            Map<String, BenchmarkData.Resolved> res = f.benchmarks().resolve(List.of(ref), t.dates(), t.today());
            if (!res.isEmpty()) {
                BenchmarkData.Resolved idx = res.values().iterator().next();
                double[] ri = idx.daily().r().clone();
                boolean pctOf = "PERCENT_OF".equals(form) && rate != null;
                if (pctOf) {
                    for (int i = 0; i < ri.length; i++) {
                        ri[i] = ri[i] * rate / 100;
                    }
                }
                String name = pctOf ? fmtPct(rate) + "% do " + idx.name() : idx.name();
                out.add(new ChartLine("index", name + periodLabel(d), rolling(d, t, ri, null, span[0], start, end)));
            }
        } catch (RuntimeException e) {
            // sem o indice, fica so a carteira
        }
        return out;
    }

    /** RIS-2: a queda desde o pico em cada dia (o pico da janela da meta), em %. */
    public static List<ChartLine> drawdownChart(GoalDefinition d, GoalFacts f, LocalDate start, LocalDate end) {
        PortfolioTimeline t = f.timeline().get();
        if (t.isEmpty()) {
            return List.of();
        }
        ScopeSeries s = series(d, f, t);
        int[] span = s.span();
        if (span[0] < 0) {
            return List.of();
        }
        double[][] l = levels(ReturnMath.daily(s));
        boolean rolling = "LAST_MONTHS".equals(d.text("window"));
        int months = d.integer("months") != null ? d.integer("months") : 12;
        List<ChartPoint> pts = new ArrayList<>();
        for (int i : days(t, span[0], start, end)) {
            int ws = rolling ? Math.max(span[0], t.indexOnOrAfter(t.dates().get(i).minusMonths(months))) : span[0];
            if (l[1][i + 1] - l[1][ws] > 0) {
                continue;
            }
            double peak = 0;
            for (int k = ws; k <= i + 1; k++) {
                peak = Math.max(peak, l[0][k]);
            }
            pts.add(new ChartPoint(t.dates().get(i), (1 - l[0][i + 1] / peak) * 100));
        }
        return List.of(new ChartLine("value", "Queda desde o pico" + (rolling ? " (" + months + " meses)" : ""), pts));
    }

    private static String periodLabel(GoalDefinition d) {
        return switch (d.text("period") == null ? "SINCE_START" : d.text("period")) {
            case "YTD" -> " no ano";
            case "LAST_12M" -> " em 12 meses";
            case "CUSTOM" -> " no período";
            default -> " desde o início";
        };
    }
}
