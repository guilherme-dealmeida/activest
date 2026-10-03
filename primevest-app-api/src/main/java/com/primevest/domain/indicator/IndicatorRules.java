package com.primevest.domain.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * As formulas da F9 (PLANO_EMPRESAS_BALANCOS.md, 5.1 a 5.3) — funcao pura: sem banco, sem
 * Spring. O {@link IndicatorService} monta a base de cada ponto e chama aqui.
 *
 * <p><b>Nulo x zero</b> (5.3, 28/09): sem provento no periodo o DY e <b>0</b>; nulo fica para
 * o que falta de fato — preco, quantidade de acoes, balanco, informe — e vem com o motivo.
 * Nenhuma formula divide pelo total de proventos.</p>
 *
 * <p>Casas: R$ por acao com 4, razao com 2, percentual com 2 (em pontos: 37,44 = 37,44%),
 * contagem inteira.</p>
 */
public final class IndicatorRules {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private IndicatorRules() {
    }

    /** Um valor calculado, ou nulo com o motivo. */
    public record Value(BigDecimal value, String note) {

        static Value of(BigDecimal v, Indicator.Unit unit) {
            return new Value(scale(v, unit), null);
        }

        static Value none(String note) {
            return new Value(null, note);
        }
    }

    /**
     * Base de um ponto de acao: o balanco ({@code company_quarters}) numa data e o preco.
     *
     * @param sharesRatio    desdobramento/grupamento/bonificacao depois da data do balanco (1 = nenhum)
     * @param unitShares     acoes por papel: 1, ou a soma da composicao da unit (KLBN11 = 5)
     * @param proventos12m   proventos por papel com data-com nos 12 meses (0 se nao houve)
     */
    public record CompanyBasis(LocalDate periodEnd, BigDecimal netIncomeParentTtm, BigDecimal equityParent,
                               BigDecimal equityParentStart, Long sharesOutstanding, BigDecimal sharesRatio,
                               int unitShares, BigDecimal dividendsDeclaredTtm, BigDecimal price,
                               BigDecimal proventos12m) {
    }

    /** Base de um ponto de FII: o informe do mes ({@code fii_monthly}) e o preco. */
    public record FundBasis(LocalDate month, BigDecimal navPerShare, Integer shareholders,
                            BigDecimal realEstate, BigDecimal realEstateCompanies, BigDecimal receivables,
                            BigDecimal totalInvested, BigDecimal liabilities, BigDecimal totalAssets,
                            BigDecimal price, BigDecimal proventos12m) {
    }

    // ------------------------------------------------------------------ acoes

    public static Value company(Indicator indicator, CompanyBasis b) {
        return switch (indicator) {
            case LPA -> lpa(b);
            case VPA -> vpa(b);
            case PL -> {
                Value lpa = lpa(b);
                if (lpa.value() == null) {
                    yield lpa;
                }
                if (b.price() == null) {
                    yield Value.none("sem cotacao");
                }
                if (lpa.value().signum() <= 0) {
                    yield Value.none("lucro 12 meses negativo ou zero");
                }
                yield Value.of(b.price().divide(rawLpa(b), MC), indicator.unit());
            }
            case PVP -> {
                Value vpa = vpa(b);
                if (vpa.value() == null) {
                    yield vpa;
                }
                if (b.price() == null) {
                    yield Value.none("sem cotacao");
                }
                if (vpa.value().signum() <= 0) {
                    yield Value.none("patrimonio liquido negativo ou zero");
                }
                yield Value.of(b.price().divide(rawVpa(b), MC), indicator.unit());
            }
            case ROE -> {
                if (b.netIncomeParentTtm() == null) {
                    yield Value.none("sem lucro de 12 meses");
                }
                if (b.equityParent() == null || b.equityParentStart() == null) {
                    yield Value.none("sem patrimonio do inicio e do fim do periodo");
                }
                BigDecimal average = b.equityParent().add(b.equityParentStart()).divide(BigDecimal.TWO, MC);
                if (average.signum() <= 0) {
                    yield Value.none("patrimonio liquido medio negativo ou zero");
                }
                yield Value.of(b.netIncomeParentTtm().divide(average, MC), indicator.unit());
            }
            case PAYOUT -> {
                if (b.netIncomeParentTtm() == null) {
                    yield Value.none("sem lucro de 12 meses");
                }
                if (b.netIncomeParentTtm().signum() <= 0) {
                    yield Value.none("lucro 12 meses negativo ou zero");
                }
                if (b.dividendsDeclaredTtm() == null) {
                    yield Value.none("sem DVA no periodo");
                }
                if (b.dividendsDeclaredTtm().signum() < 0) {
                    // 269 de ~9.700 trimestres: acumulado reapresentado na DVA (ex.: Klabin 06/2026)
                    yield Value.none("dividendos declarados negativos na DVA (reapresentacao na fonte)");
                }
                yield Value.of(b.dividendsDeclaredTtm().divide(b.netIncomeParentTtm(), MC), indicator.unit());
            }
            case DY -> dy(b.price(), b.proventos12m(), indicator);
            default -> throw new IllegalArgumentException(indicator + " nao e indicador de acao");
        };
    }

    private static Value lpa(CompanyBasis b) {
        if (b.netIncomeParentTtm() == null) {
            return Value.none("sem lucro de 12 meses");
        }
        String shares = sharesProblem(b);
        return shares != null ? Value.none(shares) : Value.of(rawLpa(b), Indicator.Unit.BRL);
    }

    private static Value vpa(CompanyBasis b) {
        if (b.equityParent() == null) {
            return Value.none("sem patrimonio liquido");
        }
        String shares = sharesProblem(b);
        return shares != null ? Value.none(shares) : Value.of(rawVpa(b), Indicator.Unit.BRL);
    }

    private static String sharesProblem(CompanyBasis b) {
        if (b.sharesOutstanding() == null || b.sharesOutstanding() <= 0) {
            return "sem quantidade de acoes (escala em revisao)";
        }
        if (b.unitShares() <= 0) {
            return "unit sem composicao";
        }
        return null;
    }

    private static BigDecimal rawLpa(CompanyBasis b) {
        return b.netIncomeParentTtm().divide(shares(b), MC).multiply(BigDecimal.valueOf(b.unitShares()));
    }

    private static BigDecimal rawVpa(CompanyBasis b) {
        return b.equityParent().divide(shares(b), MC).multiply(BigDecimal.valueOf(b.unitShares()));
    }

    /** Acoes em circulacao na data, ajustadas pelos eventos societarios posteriores. */
    private static BigDecimal shares(CompanyBasis b) {
        BigDecimal ratio = b.sharesRatio() == null ? BigDecimal.ONE : b.sharesRatio();
        return BigDecimal.valueOf(b.sharesOutstanding()).multiply(ratio);
    }

    // ------------------------------------------------------------------ FII

    public static Value fund(Indicator indicator, FundBasis b) {
        if (indicator == Indicator.DY) {
            // o DY nao depende do informe: preco e proventos do ticker
            return b == null ? Value.none("sem cotacao") : dy(b.price(), b.proventos12m(), indicator);
        }
        if (b == null || b.month() == null) {
            return Value.none("sem informe mensal");
        }
        return switch (indicator) {
            case VP_COTA -> b.navPerShare() == null ? Value.none("VP por cota nao informado")
                    : Value.of(b.navPerShare(), indicator.unit());
            case PVP -> {
                if (b.navPerShare() == null || b.navPerShare().signum() <= 0) {
                    yield Value.none("VP por cota nao informado, negativo ou zero");
                }
                if (b.price() == null) {
                    yield Value.none("sem cotacao");
                }
                yield Value.of(b.price().divide(b.navPerShare(), MC), indicator.unit());
            }
            case COTISTAS -> b.shareholders() == null ? Value.none("cotistas nao informados")
                    : Value.of(BigDecimal.valueOf(b.shareholders()), indicator.unit());
            case IMOVEIS -> share(nz(b.realEstate()).add(nz(b.realEstateCompanies())), b.totalInvested(), indicator);
            case PAPEL -> share(nz(b.receivables()), b.totalInvested(), indicator);
            case ALAVANCAGEM -> {
                if (b.totalAssets() == null || b.totalAssets().signum() <= 0) {
                    yield Value.none("ativo nao informado ou zero");
                }
                yield Value.of(nz(b.liabilities()).divide(b.totalAssets(), MC), indicator.unit());
            }
            default -> throw new IllegalArgumentException(indicator + " nao e indicador de FII");
        };
    }

    private static Value share(BigDecimal part, BigDecimal total, Indicator indicator) {
        if (total == null || total.signum() <= 0) {
            return Value.none("total investido nao informado ou zero");
        }
        return Value.of(part.divide(total, MC), indicator.unit());
    }

    // ------------------------------------------------------------------ comuns

    private static Value dy(BigDecimal price, BigDecimal proventos12m, Indicator indicator) {
        if (price == null || price.signum() <= 0) {
            return Value.none("sem cotacao");
        }
        return Value.of(nz(proventos12m).divide(price, MC), indicator.unit());
    }

    /**
     * Media da empresa (5.3): media simples dos pontos anuais <b>e do Atual</b>; nulos ficam
     * fora, zeros entram. Sem nenhum ponto, nulo.
     */
    public static BigDecimal average(List<BigDecimal> values, Indicator.Unit unit) {
        List<BigDecimal> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        BigDecimal sum = present.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        int scale = unit == Indicator.Unit.COUNT ? 0 : unit == Indicator.Unit.BRL ? 4 : 2;
        return sum.divide(BigDecimal.valueOf(present.size()), scale, RoundingMode.HALF_UP);
    }

    static BigDecimal scale(BigDecimal v, Indicator.Unit unit) {
        if (v == null) {
            return null;
        }
        return switch (unit) {
            case PERCENT -> v.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
            case RATIO -> v.setScale(2, RoundingMode.HALF_UP);
            case BRL -> v.setScale(4, RoundingMode.HALF_UP);
            case COUNT -> v.setScale(0, RoundingMode.HALF_UP);
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
