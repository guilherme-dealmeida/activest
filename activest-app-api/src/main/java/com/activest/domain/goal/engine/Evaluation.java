package com.activest.domain.goal.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * O resultado de avaliar uma meta com os fatos de hoje (docs/planos/PLANO_METAS.md, Anexo A 11). Cada numero vem com a
 * natureza (G2): OBSERVADO (dado registrado), CALCULADO (regra fixa sobre observados) ou ESTIMATIVA (depende de
 * premissa). A memoria de calculo ({@code steps}) sai na API e nao aparece na tela (D21).
 *
 * @param indicator   o numero principal da meta (valor atual, renda media, participacao...)
 * @param progressPct so nas crescentes (G5): nulo em limite e faixa
 * @param remaining   quanto falta (crescente) ou quanto esta fora (controle), na unidade {@code remainingUnit}
 * @param remainingUnit unidade do que falta: diferenca de percentuais e PP; renda que falta numa meta em % e BRL
 * @param achieved    a condicao de sucesso vale hoje
 * @param quality     COMPLETA, PARCIAL (com {@code issues}) ou INDISPONIVEL
 */
public record Evaluation(BigDecimal indicator, String indicatorLabel, Unit unit, BigDecimal target,
                         BigDecimal progressPct, BigDecimal remaining, String remainingLabel, Unit remainingUnit, boolean achieved,
                         GoalSituation situation, String situationNote, Quality quality, List<String> issues,
                         String objective, List<Figure> figures, List<Map<String, Object>> items,
                         Projection projection, List<Source> sources, List<String> steps) {

    public enum Unit {
        BRL, PERCENT, PP, SHARES, COUNT, RATIO
    }

    public enum Nature {
        OBSERVADO, CALCULADO, ESTIMATIVA
    }

    public enum Quality {
        COMPLETA, PARCIAL, INDISPONIVEL
    }

    /** Um numero da meta, rotulado (G2). */
    public record Figure(String key, String label, BigDecimal value, Unit unit, Nature nature, String note) {
    }

    /** Premissa de projecao (Anexo A 6.1): AUTOMATICA ou INFORMADA, com a data de referencia. */
    public record Assumption(String key, String label, BigDecimal value, Unit unit, String origin, String reference,
                             LocalDate referenceDate) {
    }

    /** Projecao (Anexo A 6): sempre estimativa; {@code suspended} explica por que nao ha. */
    public record Projection(List<Assumption> assumptions, List<Figure> figures, String suspended,
                             List<Map<String, Object>> path) {
    }

    /** Fonte de um dado e a data de referencia (criterio 15). */
    public record Source(String label, LocalDate date) {
    }
}
