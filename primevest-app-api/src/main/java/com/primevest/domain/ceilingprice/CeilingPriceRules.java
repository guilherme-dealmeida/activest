package com.primevest.domain.ceilingprice;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modelo PREÇO TETO V2 (docs/planos/PLANO_PRECO_TETO.md, 5.1) — função pura: sem banco, sem Spring.
 *
 * <p>Fórmulas exatamente as da planilha, na mesma ordem:</p>
 * <pre>
 *   LL_TTM = T-3 + T-2 + T-1 + T
 *   g      = ROE × (1 − payout)
 *   FC1    = LL_TTM                 VPL1 = FC1 ÷ (1+d)
 *   FC2    = FC1 × (1+g)            VPL2 = FC2 ÷ (1+d)²
 *   FC3    = FC2 × (1+g)            VPL3 = FC3 ÷ (1+d)³
 *   VT     = FC3 × (1+G) ÷ (d − G)   (só se d &gt; G)
 *   VPL_VT = VT ÷ (1+d)³
 *   Patrimônio = VPL1 + VPL2 + VPL3 + VPL_VT
 *   Teto   = Patrimônio ÷ N          Ideal = Teto × (1 − MS)
 *   Margem = Teto ÷ P − 1            Margem ideal = Ideal ÷ P − 1
 * </pre>
 *
 * <p>Percentuais entram em <b>pontos</b> (13,75) e são convertidos para fração aqui. Nada é limitado nem
 * trocado: prejuízo, crescimento negativo e teto negativo passam pela conta como são, e viram
 * {@link Issue} (validações 5–8 do pedido). Valor que não pode ser calculado sai nulo, com o motivo.</p>
 *
 * <p>Aritmética em {@link MathContext#DECIMAL128}; arredondamento só na exibição.</p>
 */
public final class CeilingPriceRules {

    static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private CeilingPriceRules() {
    }

    public enum Level { BLOQUEIO, ATENCAO, INFORMACAO }

    /** Um aviso: código estável (a tela usa), nível, campo afetado (nulo = geral) e a mensagem. */
    public record Issue(String code, Level level, String field, String message) {
    }

    /**
     * Entradas, como o usuário salvou (ou está editando). Percentuais em pontos. {@code quarters}: os quatro
     * lucros em reais, do mais antigo (T-3) ao mais recente (T); nulo = ausente.
     */
    public record Inputs(List<BigDecimal> quarters, List<String> quarterLabels, Long shares, BigDecimal roePct,
                         BigDecimal payoutPct, BigDecimal discountRatePct, BigDecimal perpetualGrowthPct,
                         BigDecimal safetyMarginPct, BigDecimal price) {
    }

    /** Limites dos alertas (D2) — {@code primevest.preco-teto.alertas.*}. */
    public record Limits(BigDecimal pctMin, BigDecimal pctMax, BigDecimal discountMin, BigDecimal discountMax,
                         BigDecimal growthMax) {

        public static Limits defaults() {
            return new Limits(BigDecimal.ZERO, HUNDRED, BigDecimal.valueOf(5), BigDecimal.valueOf(30),
                    BigDecimal.valueOf(6));
        }
    }

    /**
     * Resultado. Taxas ({@code growth}, margens, peso do terminal) em <b>fração</b> (0,0998); valores em reais
     * sem arredondar. Nulo = não calculado (o motivo está em {@code issues}).
     */
    public record Result(BigDecimal netIncomeTtm, BigDecimal growth, BigDecimal fc1, BigDecimal pv1, BigDecimal fc2,
                         BigDecimal pv2, BigDecimal fc3, BigDecimal pv3, BigDecimal terminalValue,
                         BigDecimal terminalPv, BigDecimal equityValue, BigDecimal terminalWeight,
                         BigDecimal ceilingPrice, BigDecimal idealPrice, BigDecimal ceilingMargin,
                         BigDecimal idealMargin, List<Issue> issues) {

        public boolean hasBlocking() {
            return issues.stream().anyMatch(i -> i.level() == Level.BLOQUEIO);
        }
    }

    public static Result calculate(Inputs in, Limits limits) {
        List<Issue> issues = new ArrayList<>();

        // ---- 1. LL TTM
        BigDecimal ttm = BigDecimal.ZERO;
        boolean quartersOk = in.quarters() != null && in.quarters().size() == 4;
        if (!quartersOk) {
            issues.add(new Issue("TRIMESTRE_AUSENTE", Level.BLOQUEIO, "quarters",
                    "São necessários os lucros de quatro trimestres."));
            ttm = null;
        } else {
            for (int i = 0; i < 4; i++) {
                BigDecimal q = in.quarters().get(i);
                String label = label(in, i);
                if (q == null) {
                    issues.add(new Issue("TRIMESTRE_AUSENTE", Level.BLOQUEIO, "quarters[" + i + "]",
                            "Falta o lucro do " + label + ": informe o valor. Sem ele não são calculados o LL TTM "
                                    + "e tudo o que depende dele."));
                    ttm = null;
                } else {
                    if (q.signum() < 0) {
                        issues.add(new Issue("TRIMESTRE_COM_PREJUIZO", Level.INFORMACAO, "quarters[" + i + "]",
                                "O " + label + " teve prejuízo de " + money(q.abs()) + "; ele entra na soma do LL TTM."));
                    }
                    if (ttm != null) {
                        ttm = ttm.add(q);
                    }
                }
            }
        }
        if (ttm != null) {
            if (ttm.signum() < 0) {
                issues.add(new Issue("LL_TTM_NEGATIVO", Level.ATENCAO, "netIncomeTtm",
                        "Lucro dos 12 meses negativo (" + money(ttm) + "): o Preço Teto sai negativo e não "
                                + "representa valor."));
            } else if (ttm.signum() == 0) {
                issues.add(new Issue("LL_TTM_ZERO", Level.ATENCAO, "netIncomeTtm",
                        "Lucro dos 12 meses igual a zero: o Preço Teto sai zero."));
            }
        }

        // ---- premissas
        requirePresent(in.roePct(), "roePct", "ROE", issues);
        requirePresent(in.payoutPct(), "payoutPct", "Payout", issues);
        requirePresent(in.discountRatePct(), "discountRatePct", "Taxa de desconto", issues);
        requirePresent(in.perpetualGrowthPct(), "perpetualGrowthPct", "Crescimento na perpetuidade", issues);
        requirePresent(in.safetyMarginPct(), "safetyMarginPct", "Margem de segurança", issues);
        extreme(in.roePct(), "roePct", "ROE", limits, issues);
        extreme(in.payoutPct(), "payoutPct", "Payout", limits, issues);
        extreme(in.discountRatePct(), "discountRatePct", "Taxa de desconto", limits, issues);
        extreme(in.perpetualGrowthPct(), "perpetualGrowthPct", "Crescimento na perpetuidade", limits, issues);
        if (inside(in.discountRatePct(), limits) && (in.discountRatePct().compareTo(limits.discountMin()) < 0
                || in.discountRatePct().compareTo(limits.discountMax()) > 0)) {
            issues.add(new Issue("PREMISSA_EXTREMA", Level.ATENCAO, "discountRatePct",
                    "Taxa de desconto de " + pct(in.discountRatePct()) + " fora da faixa usual ("
                            + pct(limits.discountMin()) + " a " + pct(limits.discountMax()) + "). Confira."));
        }
        if (inside(in.perpetualGrowthPct(), limits) && in.perpetualGrowthPct().compareTo(limits.growthMax()) > 0) {
            issues.add(new Issue("PREMISSA_EXTREMA", Level.ATENCAO, "perpetualGrowthPct",
                    "Crescimento na perpetuidade de " + pct(in.perpetualGrowthPct()) + " acima de "
                            + pct(limits.growthMax()) + ". Confira."));
        }

        BigDecimal d = fraction(in.discountRatePct());
        BigDecimal bigG = fraction(in.perpetualGrowthPct());
        BigDecimal ms = fraction(in.safetyMarginPct());
        BigDecimal onePlusD = d == null ? null : BigDecimal.ONE.add(d);
        if (onePlusD != null && onePlusD.signum() <= 0) {
            issues.add(new Issue("DESCONTO_INVALIDO", Level.BLOQUEIO, "discountRatePct",
                    "Taxa de desconto de " + pct(in.discountRatePct()) + " impede o desconto (1 + d ≤ 0)."));
            onePlusD = null;
        }

        // ---- 2. crescimento sustentável
        BigDecimal g = null;
        if (in.roePct() != null && in.payoutPct() != null) {
            g = fraction(in.roePct()).multiply(BigDecimal.ONE.subtract(fraction(in.payoutPct()), MC), MC);
            if (g.signum() < 0) {
                issues.add(new Issue("G_NEGATIVO", Level.ATENCAO, "growth",
                        "Crescimento sustentável negativo (" + pct(g.multiply(HUNDRED)) + "): payout acima de "
                                + "100% ou ROE negativo projetam lucro caindo."));
            }
            if (d != null && g.compareTo(d) >= 0) {
                issues.add(new Issue("G_MAIOR_QUE_D", Level.ATENCAO, "growth",
                        "Crescimento sustentável (" + pct(g.multiply(HUNDRED)) + ") igual ou acima da taxa de "
                                + "desconto (" + pct(in.discountRatePct()) + ")."));
            }
        }

        // ---- 3 a 5. períodos explícitos
        BigDecimal fc1 = ttm;
        BigDecimal pv1 = fc1 != null && onePlusD != null ? fc1.divide(onePlusD, MC) : null;
        BigDecimal fc2 = fc1 != null && g != null ? fc1.multiply(BigDecimal.ONE.add(g), MC) : null;
        BigDecimal pv2 = fc2 != null && onePlusD != null ? fc2.divide(onePlusD.pow(2, MC), MC) : null;
        BigDecimal fc3 = fc2 != null ? fc2.multiply(BigDecimal.ONE.add(g), MC) : null;
        BigDecimal pv3 = fc3 != null && onePlusD != null ? fc3.divide(onePlusD.pow(3, MC), MC) : null;

        // ---- 6 e 7. valor terminal
        boolean terminalOk = d != null && bigG != null;
        if (terminalOk && d.compareTo(bigG) <= 0) {
            issues.add(new Issue("DESCONTO_MENOR_IGUAL_G", Level.BLOQUEIO, "discountRatePct",
                    "A taxa de desconto (" + pct(in.discountRatePct()) + ") precisa ser maior que o crescimento na "
                            + "perpetuidade (" + pct(in.perpetualGrowthPct()) + "). Não são calculados o valor "
                            + "terminal, o valor do patrimônio, o Preço Teto, o Preço Ideal e as margens."));
            terminalOk = false;
        }
        BigDecimal tv = terminalOk && fc3 != null
                ? fc3.multiply(BigDecimal.ONE.add(bigG), MC).divide(d.subtract(bigG), MC) : null;
        BigDecimal tvPv = tv != null && onePlusD != null ? tv.divide(onePlusD.pow(3, MC), MC) : null;

        // ---- 8. valor do patrimônio
        BigDecimal equity = pv1 != null && pv2 != null && pv3 != null && tvPv != null
                ? pv1.add(pv2).add(pv3).add(tvPv) : null;
        BigDecimal weight = equity != null && equity.signum() != 0 ? tvPv.divide(equity, MC) : null;

        // ---- 9. Preço Teto
        boolean sharesOk = in.shares() != null && in.shares() > 0;
        if (!sharesOk) {
            issues.add(new Issue("ACOES_AUSENTES", Level.BLOQUEIO, "shares",
                    "Quantidade de ações ausente ou não positiva: não são calculados o Preço Teto, o Preço Ideal "
                            + "e as margens."));
        }
        BigDecimal ceiling = equity != null && sharesOk
                ? equity.divide(BigDecimal.valueOf(in.shares()), MC) : null;

        // ---- 10. Preço Ideal
        boolean marginOk = ms != null && ms.signum() >= 0 && ms.compareTo(BigDecimal.ONE) < 0;
        if (ms != null && !marginOk) {
            issues.add(new Issue("MARGEM_INVALIDA", Level.BLOQUEIO, "safetyMarginPct",
                    "A margem de segurança precisa estar entre 0% e 100% (exclusive)."));
        }
        BigDecimal ideal = ceiling != null && marginOk
                ? ceiling.multiply(BigDecimal.ONE.subtract(ms), MC) : null;

        // ---- 11. margens contra o preço
        boolean priceOk = in.price() != null && in.price().signum() > 0;
        if (!priceOk) {
            issues.add(new Issue("PRECO_INVALIDO", Level.BLOQUEIO, "price",
                    "Sem preço atual maior que zero: as margens não são calculadas."));
        }
        BigDecimal ceilingMargin = margin(ceiling, priceOk ? in.price() : null);
        BigDecimal idealMargin = margin(ideal, priceOk ? in.price() : null);

        return new Result(ttm, g, fc1, pv1, fc2, pv2, fc3, pv3, tv, tvPv, equity, weight, ceiling, ideal,
                ceilingMargin, idealMargin, List.copyOf(issues));
    }

    /** Valor ÷ preço − 1, em fração; nulo se faltar um dos dois. */
    public static BigDecimal margin(BigDecimal value, BigDecimal price) {
        if (value == null || price == null || price.signum() <= 0) {
            return null;
        }
        return value.divide(price, MC).subtract(BigDecimal.ONE);
    }

    // ------------------------------------------------------------------ apoio

    private static void requirePresent(BigDecimal v, String field, String name, List<Issue> issues) {
        if (v == null) {
            issues.add(new Issue("CAMPO_VAZIO", Level.BLOQUEIO, field, name + " vazio: informe o valor."));
        }
    }

    private static void extreme(BigDecimal v, String field, String name, Limits limits, List<Issue> issues) {
        if (v == null) {
            return;
        }
        if (v.compareTo(limits.pctMin()) < 0) {
            issues.add(new Issue("PREMISSA_EXTREMA", Level.ATENCAO, field,
                    name + " de " + pct(v) + " é negativo. Confira."));
        } else if (v.compareTo(limits.pctMax()) > 0) {
            issues.add(new Issue("PREMISSA_EXTREMA", Level.ATENCAO, field,
                    name + " de " + pct(v) + " está acima de " + pct(limits.pctMax()) + ". Confira."));
        }
    }

    /** Dentro de 0–100%: só aí as faixas mais apertadas valem (fora, o alerta de extremo já saiu). */
    private static boolean inside(BigDecimal v, Limits limits) {
        return v != null && v.compareTo(limits.pctMin()) >= 0 && v.compareTo(limits.pctMax()) <= 0;
    }

    private static String label(Inputs in, int i) {
        if (in.quarterLabels() != null && in.quarterLabels().size() > i && in.quarterLabels().get(i) != null) {
            return in.quarterLabels().get(i);
        }
        return "trimestre T" + (i == 3 ? "" : "-" + (3 - i));
    }

    static BigDecimal fraction(BigDecimal pct) {
        return pct == null ? null : pct.divide(HUNDRED, MC);
    }

    // ------------------------------------------------------------------ formatação das mensagens

    static String pct(BigDecimal points) {
        return number(points.setScale(2, RoundingMode.HALF_UP), "#,##0.00") + "%";
    }

    static String money(BigDecimal v) {
        BigDecimal abs = v.abs();
        String s;
        if (abs.compareTo(new BigDecimal("1e9")) >= 0) {
            s = number(abs.divide(new BigDecimal("1e9"), MC).setScale(2, RoundingMode.HALF_UP), "#,##0.00") + " bi";
        } else if (abs.compareTo(new BigDecimal("1e6")) >= 0) {
            s = number(abs.divide(new BigDecimal("1e6"), MC).setScale(2, RoundingMode.HALF_UP), "#,##0.00") + " mi";
        } else {
            s = number(abs.setScale(2, RoundingMode.HALF_UP), "#,##0.00");
        }
        return (v.signum() < 0 ? "−R$ " : "R$ ") + s;
    }

    private static String number(BigDecimal v, String pattern) {
        DecimalFormat f = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        return f.format(v);
    }
}
