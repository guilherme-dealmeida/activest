package com.activest.domain.indicator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * As formulas da F9 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.1 a 5.3) — funcao pura: sem banco, sem
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
                               int unitShares, BigDecimal dividendsPaidTtm, BigDecimal price,
                               BigDecimal dividends12m, Accounts accounts) {

        /** So com o que os indicadores da F9 usam (sem as contas da tela de Ativos). */
        public CompanyBasis(LocalDate periodEnd, BigDecimal netIncomeParentTtm, BigDecimal equityParent,
                            BigDecimal equityParentStart, Long sharesOutstanding, BigDecimal sharesRatio,
                            int unitShares, BigDecimal dividendsPaidTtm, BigDecimal price, BigDecimal dividends12m) {
            this(periodEnd, netIncomeParentTtm, equityParent, equityParentStart, sharesOutstanding, sharesRatio,
                    unitShares, dividendsPaidTtm, price, dividends12m, Accounts.NONE);
        }

        public CompanyBasis {
            accounts = accounts == null ? Accounts.NONE : accounts;
        }

        CompanyBasis withShares(Long shares, Accounts a) {
            return new CompanyBasis(periodEnd, netIncomeParentTtm, equityParent, equityParentStart, shares,
                    sharesRatio, unitShares, dividendsPaidTtm, price, dividends12m, a);
        }
    }

    /**
     * Contas da tela de Ativos (docs/planos/PLANO_TELA_ATIVOS.md, 2.5): fluxos de 12 meses e posicao do balanco do
     * ponto, a posicao do balanco de 12 meses antes ({@code *Start}, para as medias do ROA, do ROIC e do giro), os
     * 12 meses de 5 anos antes (CAGR) e o valor de mercado da companhia (D3), ja calculado, com a nota.
     *
     * <p>PL aqui e o <b>consolidado</b> ({@code equity}, com nao controladores), coerente com ativo, divida e EBIT
     * consolidados; o ROE e o VPA seguem com o da controladora. Lucro liquido = consolidado, pelo mesmo motivo
     * (margem, ROA, CAGR). IR/CSLL como a CVM informa: negativo quando e despesa.</p>
     */
    public record Accounts(BigDecimal revenueTtm, BigDecimal ebitTtm, BigDecimal depreciationTtm,
                           BigDecimal grossProfitTtm, BigDecimal netIncomeTtm, BigDecimal pretaxIncomeTtm,
                           BigDecimal incomeTaxTtm, BigDecimal equity, BigDecimal totalAssets, BigDecimal cash,
                           BigDecimal grossDebt, BigDecimal currentAssets, BigDecimal currentLiabilities,
                           BigDecimal equityStart, BigDecimal totalAssetsStart, BigDecimal cashStart,
                           BigDecimal grossDebtStart, BigDecimal revenueTtm5y, BigDecimal netIncomeTtm5y,
                           BigDecimal marketValue, String marketValueNote) {

        public static final Accounts NONE = new Accounts(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, "sem valor de mercado");

        Accounts withMarketValue(BigDecimal value, String note) {
            return new Accounts(revenueTtm, ebitTtm, depreciationTtm, grossProfitTtm, netIncomeTtm, pretaxIncomeTtm,
                    incomeTaxTtm, equity, totalAssets, cash, grossDebt, currentAssets, currentLiabilities,
                    equityStart, totalAssetsStart, cashStart, grossDebtStart, revenueTtm5y, netIncomeTtm5y, value,
                    note);
        }

        /** EBIT + depreciacao e amortizacao; nulo sem um dos dois. */
        BigDecimal ebitda() {
            return ebitTtm == null || depreciationTtm == null ? null : ebitTtm.add(depreciationTtm);
        }

        BigDecimal netDebt() {
            return grossDebt == null || cash == null ? null : grossDebt.subtract(cash);
        }

        BigDecimal netDebtStart() {
            return grossDebtStart == null || cashStart == null ? null : grossDebtStart.subtract(cashStart);
        }
    }

    /** Base de um ponto de FII: o informe do mes ({@code fii_monthly}) e o preco. */
    public record FundBasis(LocalDate month, BigDecimal navPerShare, Integer shareholders,
                            BigDecimal realEstate, BigDecimal realEstateCompanies, BigDecimal receivables,
                            BigDecimal totalInvested, BigDecimal liabilities, BigDecimal totalAssets,
                            BigDecimal price, BigDecimal dividends12m) {
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
                // Base: dividendos + JCP PAGOS aos acionistas da controladora (DFC), desde 10/10/2026
                // (docs/planos/PLANO_PRECO_TETO.md, 1.5 e D4). A DVA dava 0% para a Vale e para 167 de 357 no DFP 2025.
                // Acima de 100% e real (pagou mais que o lucro do periodo) e fica como esta.
                if (b.dividendsPaidTtm() == null) {
                    yield Value.none("sem fluxo de caixa de 12 meses");
                }
                if (b.dividendsPaidTtm().signum() < 0) {
                    yield Value.none("dividendos pagos negativos na DFC (reapresentacao na fonte)");
                }
                yield Value.of(b.dividendsPaidTtm().divide(b.netIncomeParentTtm(), MC), indicator.unit());
            }
            case DY -> dy(b.price(), b.dividends12m(), indicator);
            default -> assets(indicator, b);
        };
    }

    // ------------------------------------------------------------------ acoes, tela de Ativos (2.5)

    private static Value assets(Indicator indicator, CompanyBasis b) {
        Accounts a = b.accounts();
        Indicator.Unit u = indicator.unit();
        return switch (indicator) {
            case PSR -> priceOver(b, a.revenueTtm(), "receita 12 meses", u);
            case P_EBIT -> priceOver(b, a.ebitTtm(), "EBIT 12 meses", u);
            case P_EBITDA -> priceOver(b, a.ebitda(), "EBITDA 12 meses", u);
            case P_ATIVO -> priceOver(b, a.totalAssets(), "ativo total", u);
            case P_CAP_GIRO -> priceOver(b, diff(a.currentAssets(), a.currentLiabilities()), "capital de giro", u);
            case P_ATIVO_CIRC_LIQ -> {
                BigDecimal liabilities = diff(a.totalAssets(), a.equity());
                yield priceOver(b, diff(a.currentAssets(), liabilities), "ativo circulante liquido", u);
            }
            case EV_EBIT -> enterprise(a, a.ebitTtm(), "EBIT 12 meses", u);
            case EV_EBITDA -> enterprise(a, a.ebitda(), "EBITDA 12 meses", u);
            case DIV_LIQ_EBIT -> over(a.netDebt(), "divida liquida", a.ebitTtm(), "EBIT 12 meses", u);
            case DIV_LIQ_EBITDA -> over(a.netDebt(), "divida liquida", a.ebitda(), "EBITDA 12 meses", u);
            case DIV_BRUTA_PATRIMONIO -> over(a.grossDebt(), "divida bruta", a.equity(), "patrimonio liquido", u);
            case PATRIMONIO_ATIVOS -> over(a.equity(), "patrimonio liquido", a.totalAssets(), "ativo total", u);
            case PASSIVOS_ATIVOS -> over(diff(a.totalAssets(), a.equity()), "patrimonio liquido",
                    a.totalAssets(), "ativo total", u);
            case LIQUIDEZ_CORRENTE -> over(a.currentAssets(), "ativo circulante", a.currentLiabilities(),
                    "passivo circulante", u);
            case MARGEM_BRUTA -> over(a.grossProfitTtm(), "lucro bruto 12 meses", a.revenueTtm(), "receita 12 meses", u);
            case MARGEM_EBITDA -> over(a.ebitda(), "EBITDA 12 meses", a.revenueTtm(), "receita 12 meses", u);
            case MARGEM_EBIT -> over(a.ebitTtm(), "EBIT 12 meses", a.revenueTtm(), "receita 12 meses", u);
            case MARGEM_LIQUIDA -> over(a.netIncomeTtm(), "lucro liquido 12 meses", a.revenueTtm(),
                    "receita 12 meses", u);
            case ROA -> over(a.netIncomeTtm(), "lucro liquido 12 meses", mean(a.totalAssets(), a.totalAssetsStart()),
                    "ativo total do inicio e do fim do periodo", u);
            case GIRO_ATIVOS -> over(a.revenueTtm(), "receita 12 meses", mean(a.totalAssets(), a.totalAssetsStart()),
                    "ativo total do inicio e do fim do periodo", u);
            case ROIC -> roic(a, u);
            case CAGR_RECEITAS_5A -> cagr(a.revenueTtm(), a.revenueTtm5y(), "receita", u);
            case CAGR_LUCROS_5A -> cagr(a.netIncomeTtm(), a.netIncomeTtm5y(), "lucro liquido", u);
            default -> throw new IllegalArgumentException(indicator + " nao e indicador de acao");
        };
    }

    /** Preco do ticker / (valor da companhia / acoes, x acoes da unit); base negativa ou zero vira nulo. */
    private static Value priceOver(CompanyBasis b, BigDecimal amount, String what, Indicator.Unit u) {
        if (amount == null) {
            return Value.none("sem " + what);
        }
        String shares = sharesProblem(b);
        if (shares != null) {
            return Value.none(shares);
        }
        if (b.price() == null) {
            return Value.none("sem cotacao");
        }
        if (amount.signum() <= 0) {
            return Value.none(what + " negativo ou zero");
        }
        BigDecimal perShare = amount.divide(shares(b), MC).multiply(BigDecimal.valueOf(b.unitShares()));
        return Value.of(b.price().divide(perShare, MC), u);
    }

    /** Valor de firma (valor de mercado + divida liquida) / base, por empresa (D18). */
    private static Value enterprise(Accounts a, BigDecimal base, String what, Indicator.Unit u) {
        if (a.marketValue() == null) {
            return Value.none(a.marketValueNote() == null ? "sem valor de mercado" : a.marketValueNote());
        }
        if (a.netDebt() == null) {
            return Value.none("sem divida liquida");
        }
        Value v = over(a.marketValue().add(a.netDebt()), "valor de firma", base, what, u);
        // nota do valor de mercado estimado (classe sem cotacao, D3) vai junto do valor
        return v.value() != null && a.marketValueNote() != null ? new Value(v.value(), a.marketValueNote()) : v;
    }

    /** Numerador / denominador; denominador negativo ou zero vira nulo (numerador negativo fica). */
    private static Value over(BigDecimal num, String numName, BigDecimal den, String denName, Indicator.Unit u) {
        if (num == null) {
            return Value.none("sem " + numName);
        }
        if (den == null) {
            return Value.none("sem " + denName);
        }
        if (den.signum() <= 0) {
            return Value.none(denName + " negativo ou zero");
        }
        return Value.of(num.divide(den, MC), u);
    }

    /**
     * ROIC (D19): EBIT 12m x (1 - aliquota) / (PL + divida liquida) medio, aliquota = -(IR/CSLL) / LAIR (o IR vem
     * negativo como despesa). Aliquota fora de 0-100% vira nulo com o motivo.
     */
    private static Value roic(Accounts a, Indicator.Unit u) {
        if (a.ebitTtm() == null) {
            return Value.none("sem EBIT 12 meses");
        }
        if (a.pretaxIncomeTtm() == null || a.incomeTaxTtm() == null) {
            return Value.none("sem resultado antes dos tributos ou IR/CSLL de 12 meses");
        }
        if (a.pretaxIncomeTtm().signum() <= 0) {
            return Value.none("resultado antes dos tributos negativo ou zero");
        }
        BigDecimal rate = a.incomeTaxTtm().negate().divide(a.pretaxIncomeTtm(), MC);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            return Value.none("aliquota efetiva de IR/CSLL fora de 0-100% ("
                    + rate.multiply(HUNDRED).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%)");
        }
        BigDecimal end = sumOrNull(a.equity(), a.netDebt());
        BigDecimal start = sumOrNull(a.equityStart(), a.netDebtStart());
        BigDecimal invested = mean(end, start);
        if (invested == null) {
            return Value.none("sem capital investido (PL + divida liquida) do inicio e do fim do periodo");
        }
        if (invested.signum() <= 0) {
            return Value.none("capital investido medio negativo ou zero");
        }
        BigDecimal nopat = a.ebitTtm().multiply(BigDecimal.ONE.subtract(rate), MC);
        return Value.of(nopat.divide(invested, MC), u);
    }

    /** CAGR de 5 anos (D20): vazio com valor ausente, negativo ou zero no inicio ou no fim. */
    private static Value cagr(BigDecimal now, BigDecimal before, String what, Indicator.Unit u) {
        if (now == null || before == null) {
            return Value.none("sem " + what + " de 12 meses de hoje e de 5 anos antes");
        }
        if (now.signum() <= 0 || before.signum() <= 0) {
            return Value.none(what + " de 12 meses negativo ou zero no inicio ou no fim");
        }
        double g = Math.pow(now.divide(before, MC).doubleValue(), 1.0 / 5) - 1;
        return Value.of(BigDecimal.valueOf(g), u);
    }

    private static BigDecimal diff(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b);
    }

    private static BigDecimal sumOrNull(BigDecimal a, BigDecimal b) {
        return a == null || b == null ? null : a.add(b);
    }

    /** Media do inicio e do fim; nulo sem um dos dois. */
    private static BigDecimal mean(BigDecimal end, BigDecimal start) {
        return end == null || start == null ? null : end.add(start).divide(BigDecimal.TWO, MC);
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
            return b == null ? Value.none("sem cotacao") : dy(b.price(), b.dividends12m(), indicator);
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

    private static Value dy(BigDecimal price, BigDecimal dividends12m, Indicator indicator) {
        if (price == null || price.signum() <= 0) {
            return Value.none("sem cotacao");
        }
        return Value.of(nz(dividends12m).divide(price, MC), indicator.unit());
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
