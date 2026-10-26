package com.activest.domain.goal.engine;

import com.activest.domain.goal.engine.Evaluation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Monta uma {@link Evaluation} aos poucos; arredonda pela D19 (R$ 2 casas, % 2 casas, estimativas a R$ 100). */
final class EvaluationBuilder {

    BigDecimal indicator;
    String indicatorLabel;
    Unit unit = Unit.BRL;
    BigDecimal target;
    BigDecimal progressPct;
    BigDecimal remaining;
    String remainingLabel;
    /** Nulo: a do indicador. */
    Unit remainingUnit;
    boolean achieved;
    GoalSituation situation;
    String situationNote;
    Quality quality = Quality.COMPLETA;
    final LinkedHashSet<String> issues = new LinkedHashSet<>();
    String objective;
    final List<Figure> figures = new ArrayList<>();
    final List<Map<String, Object>> items = new ArrayList<>();
    Projection projection;
    final List<Source> sources = new ArrayList<>();
    final List<String> steps = new ArrayList<>();

    EvaluationBuilder indicator(Double v, String label, Unit u) {
        this.indicator = round(v, u);
        this.indicatorLabel = label;
        this.unit = u;
        return this;
    }

    EvaluationBuilder target(Double v) {
        this.target = round(v, unit);
        return this;
    }

    EvaluationBuilder progress(Double current, Double goal) {
        if (current != null && goal != null && goal > 0) {
            this.progressPct = scale(Math.max(0, current) / goal * 100, 2);
        }
        return this;
    }

    EvaluationBuilder remaining(Double v, String label) {
        return remaining(v, label, unit);
    }

    /** O que falta numa unidade propria (diferenca de percentuais em p.p.; renda em R$ numa meta em %). */
    EvaluationBuilder remaining(Double v, String label, Unit u) {
        this.remaining = round(v, u);
        this.remainingLabel = label;
        this.remainingUnit = u;
        return this;
    }

    EvaluationBuilder fig(String key, String label, Double value, Unit u, Nature n) {
        return fig(key, label, value, u, n, null);
    }

    EvaluationBuilder fig(String key, String label, Double value, Unit u, Nature n, String note) {
        figures.add(new Figure(key, label, n == Nature.ESTIMATIVA ? estimate(value, u) : round(value, u), u, n, note));
        return this;
    }

    EvaluationBuilder step(String s) {
        steps.add(s);
        return this;
    }

    EvaluationBuilder source(String label, LocalDate date) {
        if (date != null && sources.stream().noneMatch(s -> s.label().equals(label))) {
            sources.add(new Source(label, date));
        }
        return this;
    }

    /** Dado faltando: PARCIAL (o resto segue calculado). */
    EvaluationBuilder partial(String issue) {
        issues.add(issue);
        if (quality == Quality.COMPLETA) {
            quality = Quality.PARCIAL;
        }
        return this;
    }

    /** Calculo suspenso (G3): sem dados suficientes. */
    EvaluationBuilder unavailable(String reason) {
        issues.add(reason);
        quality = Quality.INDISPONIVEL;
        situation = GoalSituation.SEM_DADOS;
        situationNote = reason;
        return this;
    }

    Evaluation build() {
        return new Evaluation(indicator, indicatorLabel, unit, target, progressPct, remaining, remainingLabel,
                remainingUnit != null ? remainingUnit : unit, achieved,
                situation != null ? situation : GoalSituation.EM_ANDAMENTO, situationNote, quality,
                List.copyOf(issues), objective, List.copyOf(figures), List.copyOf(items), projection,
                List.copyOf(sources), List.copyOf(steps));
    }

    static BigDecimal round(Double v, Unit u) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return null;
        }
        return switch (u) {
            case BRL -> scale(v, 2);
            case PERCENT, PP -> scale(v, 2);
            case SHARES -> scale(v, 8);
            case COUNT -> scale(v, 0);
            case RATIO -> scale(v, 4);
        };
    }

    /** D19: valores estimados em R$ arredondados a R$ 100; os demais como {@link #round}. */
    static BigDecimal estimate(Double v, Unit u) {
        if (v == null || v.isNaN() || v.isInfinite()) {
            return null;
        }
        if (u == Unit.BRL && Math.abs(v) >= 100) {
            return BigDecimal.valueOf(Math.round(v / 100.0) * 100L).setScale(2, RoundingMode.HALF_UP);
        }
        return round(v, u);
    }

    static BigDecimal scale(double v, int s) {
        return BigDecimal.valueOf(v).setScale(s, RoundingMode.HALF_UP);
    }
}
