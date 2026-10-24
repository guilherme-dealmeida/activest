package com.activest.domain.indicator;

import com.activest.domain.indicator.IndicatorRules.CompanyBasis;
import com.activest.domain.indicator.IndicatorRules.FundBasis;
import com.activest.domain.indicator.IndicatorRules.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Formulas da F9 (docs/planos/PLANO_EMPRESAS_BALANCOS.md, 5.1 a 5.3). Numeros da Petrobras 2025 do DFP
 * real (conferidos no plano, 1.1: LPA 8,54, VPA 32,26, payout 37,4%) e do HGLG11 08/2026.
 */
class IndicatorRulesTest {

    static final LocalDate DEC_2025 = LocalDate.of(2025, 12, 31);

    /** Petrobras, DFP 2025: lucro da controladora, PL da controladora, acoes, dividendos + JCP da DVA. */
    static CompanyBasis petrobras(BigDecimal price, BigDecimal dividends) {
        return new CompanyBasis(DEC_2025, new BigDecimal("110129000000"), new BigDecimal("415786000000"),
                new BigDecimal("366006000000"), 12_888_732_761L, BigDecimal.ONE, 1,
                new BigDecimal("41236000000"), price, dividends);
    }

    static BigDecimal v(Indicator i, CompanyBasis b) {
        return IndicatorRules.company(i, b).value();
    }

    @Test
    @DisplayName("Petrobras 2025: LPA, VPA e payout batem com o conferido no plano")
    void petrobras2025() {
        CompanyBasis b = petrobras(new BigDecimal("30.00"), new BigDecimal("3.21"));

        assertThat(v(Indicator.LPA, b)).isEqualByComparingTo("8.5446");
        assertThat(v(Indicator.VPA, b)).isEqualByComparingTo("32.2596");
        assertThat(v(Indicator.PAYOUT, b)).isEqualByComparingTo("37.44");
        assertThat(v(Indicator.PL, b)).isEqualByComparingTo("3.51");   // 30 / 8,5446
        assertThat(v(Indicator.PVP, b)).isEqualByComparingTo("0.93");  // 30 / 32,2598
        assertThat(v(Indicator.DY, b)).isEqualByComparingTo("10.70");  // 3,21 / 30
        // ROE com PL medio: 110,129 / ((415,786 + 366,006) / 2) — igual ao endpoint
        assertThat(v(Indicator.ROE, b)).isEqualByComparingTo("28.17");
    }

    @Test
    @DisplayName("Sem provento no periodo: DY = 0, nao nulo (os 23 FIIs que ainda nao distribuiram)")
    void withoutDividendIsZero() {
        Value dy = IndicatorRules.company(Indicator.DY, petrobras(new BigDecimal("30.00"), BigDecimal.ZERO));
        assertThat(dy.value()).isEqualByComparingTo("0");
        assertThat(dy.note()).isNull();

        FundBasis fundEntity = new FundBasis(LocalDate.of(2026, 8, 1), new BigDecimal("78.0873"), 182, null, null, null,
                null, null, null, new BigDecimal("95.00"), BigDecimal.ZERO);
        assertThat(IndicatorRules.fund(Indicator.DY, fundEntity).value()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Nulo com motivo so quando falta dado: preco, acoes, lucro negativo, informe")
    void nullsWithReason() {
        Value withoutPrice = IndicatorRules.company(Indicator.DY, petrobras(null, BigDecimal.ONE));
        assertThat(withoutPrice.value()).isNull();
        assertThat(withoutPrice.note()).isEqualTo("sem cotacao");

        CompanyBasis loss = new CompanyBasis(DEC_2025, new BigDecimal("-1000"), new BigDecimal("5000"), null,
                100L, BigDecimal.ONE, 1, new BigDecimal("10"), new BigDecimal("10"), BigDecimal.ZERO);
        assertThat(IndicatorRules.company(Indicator.LPA, loss).value()).isEqualByComparingTo("-10");
        assertThat(IndicatorRules.company(Indicator.PL, loss).note()).isEqualTo("lucro 12 meses negativo ou zero");
        assertThat(IndicatorRules.company(Indicator.PAYOUT, loss).value()).isNull();
        assertThat(IndicatorRules.company(Indicator.ROE, loss).note())
                .isEqualTo("sem patrimonio do inicio e do fim do periodo");

        CompanyBasis withoutShares = new CompanyBasis(DEC_2025, new BigDecimal("1000"), new BigDecimal("5000"), null,
                null, BigDecimal.ONE, 1, null, new BigDecimal("10"), BigDecimal.ZERO);
        assertThat(IndicatorRules.company(Indicator.LPA, withoutShares).note())
                .isEqualTo("sem quantidade de acoes (escala em revisao)");
        assertThat(IndicatorRules.company(Indicator.PVP, withoutShares).value()).isNull();

        FundBasis withoutReport = new FundBasis(null, null, null, null, null, null, null, null, null,
                new BigDecimal("100"), BigDecimal.ZERO);
        assertThat(IndicatorRules.fund(Indicator.PVP, withoutReport).note()).isEqualTo("sem informe mensal");
        assertThat(IndicatorRules.fund(Indicator.DY, withoutReport).value()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Payout com dividendos negativos na DVA (reapresentacao): nulo, nao negativo")
    void negativePayout() {
        CompanyBasis b = new CompanyBasis(DEC_2025, new BigDecimal("1000"), new BigDecimal("5000"), null, 100L,
                BigDecimal.ONE, 1, new BigDecimal("-191"), new BigDecimal("10"), BigDecimal.ZERO);
        assertThat(IndicatorRules.company(Indicator.PAYOUT, b).value()).isNull();
    }

    @Test
    @DisplayName("Unit: LPA e VPA x acoes da unit; desdobramento depois do balanco dobra a quantidade")
    void unitAndSplit() {
        CompanyBasis unit = new CompanyBasis(DEC_2025, new BigDecimal("1000"), new BigDecimal("5000"), null, 1000L,
                BigDecimal.ONE, 5, null, new BigDecimal("20"), BigDecimal.ZERO);
        assertThat(v(Indicator.LPA, unit)).isEqualByComparingTo("5");   // 1 por acao x 5
        assertThat(v(Indicator.PL, unit)).isEqualByComparingTo("4");    // 20 / 5

        CompanyBasis split = new CompanyBasis(DEC_2025, new BigDecimal("1000"), new BigDecimal("5000"), null,
                1000L, new BigDecimal("2"), 1, null, new BigDecimal("10"), BigDecimal.ZERO);
        assertThat(v(Indicator.LPA, split)).isEqualByComparingTo("0.5");

        CompanyBasis withoutComposition = new CompanyBasis(DEC_2025, new BigDecimal("1000"), new BigDecimal("5000"), null,
                1000L, BigDecimal.ONE, 0, null, new BigDecimal("10"), BigDecimal.ZERO);
        assertThat(IndicatorRules.company(Indicator.LPA, withoutComposition).note()).isEqualTo("unit sem composicao");
    }

    @Test
    @DisplayName("FII HGLG11 08/2026: P/VP, VP por cota, cotistas, imoveis, papel e alavancagem")
    void hglg() {
        FundBasis b = new FundBasis(LocalDate.of(2026, 8, 1), new BigDecimal("165.951408188109"), 608345,
                new BigDecimal("7057544916.78"), new BigDecimal("1051691815.4"), new BigDecimal("31703049.55"),
                new BigDecimal("8289415918.47"), new BigDecimal("1282893011.09"), new BigDecimal("8850561499.23"),
                new BigDecimal("147.86"), new BigDecimal("13.34"));

        assertThat(IndicatorRules.fund(Indicator.VP_COTA, b).value()).isEqualByComparingTo("165.9514");
        assertThat(IndicatorRules.fund(Indicator.PVP, b).value()).isEqualByComparingTo("0.89");
        assertThat(IndicatorRules.fund(Indicator.COTISTAS, b).value()).isEqualByComparingTo("608345");
        assertThat(IndicatorRules.fund(Indicator.IMOVEIS, b).value()).isEqualByComparingTo("97.83");
        assertThat(IndicatorRules.fund(Indicator.PAPEL, b).value()).isEqualByComparingTo("0.38");
        assertThat(IndicatorRules.fund(Indicator.ALAVANCAGEM, b).value()).isEqualByComparingTo("14.50");
        assertThat(IndicatorRules.fund(Indicator.DY, b).value()).isEqualByComparingTo("9.02");
    }

    // ------------------------------------------------------------------ tela de Ativos (PLANO_TELA_ATIVOS, 2.5)

    static BigDecimal mi(long millions) {
        return BigDecimal.valueOf(millions).multiply(BigDecimal.valueOf(1_000_000));
    }

    /**
     * Petrobras, DFP 2025 (contas reais, conferidas na A1); o balanco de 12 meses antes, os 12 meses de 5 anos antes
     * e o valor de mercado (R$ 900 bi) sao sinteticos, so para a conta.
     */
    static IndicatorRules.Accounts petrobrasAccounts(BigDecimal marketValue, String note) {
        return new IndicatorRules.Accounts(mi(497_549), mi(145_628), mi(84_388), mi(236_998), mi(110_605),
                mi(150_599), mi(-39_994), mi(417_587), mi(1_223_389), mi(50_608), mi(384_025), mi(140_026),
                mi(198_368), mi(400_000), mi(1_100_000), mi(40_000), mi(350_000), mi(300_000), mi(50_000),
                marketValue, note);
    }

    static CompanyBasis petrobrasFull(IndicatorRules.Accounts a) {
        return new CompanyBasis(DEC_2025, new BigDecimal("110129000000"), new BigDecimal("415786000000"),
                new BigDecimal("366006000000"), 12_888_732_761L, BigDecimal.ONE, 1,
                new BigDecimal("41236000000"), new BigDecimal("30.00"), BigDecimal.ZERO, a);
    }

    @Test
    @DisplayName("Indicadores novos de acoes: as 23 formulas com as contas da Petrobras 2025")
    void newIndicators() {
        CompanyBasis b = petrobrasFull(petrobrasAccounts(new BigDecimal("900000000000"), null));
        assertThat(v(Indicator.PSR, b)).isEqualByComparingTo("0.78");
        assertThat(v(Indicator.P_EBIT, b)).isEqualByComparingTo("2.66");
        assertThat(v(Indicator.P_EBITDA, b)).isEqualByComparingTo("1.68");      // EBITDA = EBIT + D&A
        assertThat(v(Indicator.P_ATIVO, b)).isEqualByComparingTo("0.32");
        assertThat(v(Indicator.EV_EBIT, b)).isEqualByComparingTo("8.47");       // (900 bi + divida liquida) / EBIT
        assertThat(v(Indicator.EV_EBITDA, b)).isEqualByComparingTo("5.36");
        assertThat(v(Indicator.DIV_LIQ_EBIT, b)).isEqualByComparingTo("2.29");
        assertThat(v(Indicator.DIV_LIQ_EBITDA, b)).isEqualByComparingTo("1.45");
        assertThat(v(Indicator.DIV_BRUTA_PATRIMONIO, b)).isEqualByComparingTo("0.92");
        assertThat(v(Indicator.PATRIMONIO_ATIVOS, b)).isEqualByComparingTo("34.13");
        assertThat(v(Indicator.PASSIVOS_ATIVOS, b)).isEqualByComparingTo("65.87");
        assertThat(v(Indicator.LIQUIDEZ_CORRENTE, b)).isEqualByComparingTo("0.71");
        assertThat(v(Indicator.MARGEM_BRUTA, b)).isEqualByComparingTo("47.63");
        assertThat(v(Indicator.MARGEM_EBITDA, b)).isEqualByComparingTo("46.23");
        assertThat(v(Indicator.MARGEM_EBIT, b)).isEqualByComparingTo("29.27");
        assertThat(v(Indicator.MARGEM_LIQUIDA, b)).isEqualByComparingTo("22.23");
        assertThat(v(Indicator.ROA, b)).isEqualByComparingTo("9.52");           // ativo medio do inicio e do fim
        assertThat(v(Indicator.GIRO_ATIVOS, b)).isEqualByComparingTo("0.43");
        assertThat(v(Indicator.ROIC, b)).isEqualByComparingTo("14.64");         // aliquota 39.994 / 150.599
        assertThat(v(Indicator.CAGR_RECEITAS_5A, b)).isEqualByComparingTo("10.65");
        assertThat(v(Indicator.CAGR_LUCROS_5A, b)).isEqualByComparingTo("17.21");
        // capital de giro e ativo circulante liquido negativos na Petrobras: nulo com o motivo
        assertThat(IndicatorRules.company(Indicator.P_CAP_GIRO, b).note()).isEqualTo("capital de giro negativo ou zero");
        assertThat(IndicatorRules.company(Indicator.P_ATIVO_CIRC_LIQ, b).note())
                .isEqualTo("ativo circulante liquido negativo ou zero");
    }

    @Test
    @DisplayName("Indicadores novos: nulos com o motivo, nunca zero inventado")
    void newIndicatorsNulls() {
        IndicatorRules.Accounts none = IndicatorRules.Accounts.NONE;
        CompanyBasis empty = petrobrasFull(none);
        assertThat(IndicatorRules.company(Indicator.PSR, empty).note()).isEqualTo("sem receita 12 meses");
        assertThat(IndicatorRules.company(Indicator.EV_EBIT, empty).note()).isEqualTo("sem valor de mercado");
        assertThat(IndicatorRules.company(Indicator.ROIC, empty).note()).isEqualTo("sem EBIT 12 meses");
        assertThat(IndicatorRules.company(Indicator.CAGR_LUCROS_5A, empty).note())
                .isEqualTo("sem lucro liquido de 12 meses de hoje e de 5 anos antes");

        IndicatorRules.Accounts a = petrobrasAccounts(null, "sem cotacao das acoes ON");
        // EBIT negativo: P/EBIT, EV/EBIT e Div. liq./EBIT nulos; a margem EBIT negativa fica
        IndicatorRules.Accounts loss = new IndicatorRules.Accounts(a.revenueTtm(), mi(-10_000), BigDecimal.ZERO,
                a.grossProfitTtm(), mi(-50), mi(-60), mi(10), a.equity(), a.totalAssets(), a.cash(), a.grossDebt(),
                a.currentAssets(), a.currentLiabilities(), a.equityStart(), a.totalAssetsStart(), a.cashStart(),
                a.grossDebtStart(), a.revenueTtm5y(), a.netIncomeTtm5y(), new BigDecimal("900000000000"), null);
        CompanyBasis b = petrobrasFull(loss);
        assertThat(IndicatorRules.company(Indicator.P_EBIT, b).note()).isEqualTo("EBIT 12 meses negativo ou zero");
        assertThat(IndicatorRules.company(Indicator.EV_EBIT, b).note()).isEqualTo("EBIT 12 meses negativo ou zero");
        assertThat(IndicatorRules.company(Indicator.DIV_LIQ_EBIT, b).note()).isEqualTo("EBIT 12 meses negativo ou zero");
        assertThat(v(Indicator.MARGEM_EBIT, b)).isNegative();
        assertThat(IndicatorRules.company(Indicator.ROIC, b).note())
                .isEqualTo("resultado antes dos tributos negativo ou zero");
        assertThat(IndicatorRules.company(Indicator.CAGR_LUCROS_5A, b).note())
                .isEqualTo("lucro liquido de 12 meses negativo ou zero no inicio ou no fim");
        // valor de mercado sem cotacao: o motivo vem do calculo do valor
        assertThat(IndicatorRules.company(Indicator.EV_EBITDA, petrobrasFull(a)).note())
                .isEqualTo("sem cotacao das acoes ON");
    }

    @Test
    @DisplayName("ROIC: aliquota efetiva fora de 0-100% vira nulo com o motivo; valor estimado leva a nota da D3")
    void roicAndEstimatedMarketValue() {
        IndicatorRules.Accounts a = petrobrasAccounts(new BigDecimal("900000000000"),
                "ON sem negociacao: avaliada pelo preco de PETR4");
        IndicatorRules.Accounts credit = new IndicatorRules.Accounts(a.revenueTtm(), a.ebitTtm(), a.depreciationTtm(),
                a.grossProfitTtm(), a.netIncomeTtm(), mi(100), mi(20), a.equity(), a.totalAssets(), a.cash(),
                a.grossDebt(), a.currentAssets(), a.currentLiabilities(), a.equityStart(), a.totalAssetsStart(),
                a.cashStart(), a.grossDebtStart(), a.revenueTtm5y(), a.netIncomeTtm5y(), a.marketValue(),
                a.marketValueNote());
        assertThat(IndicatorRules.company(Indicator.ROIC, petrobrasFull(credit)).note())
                .isEqualTo("aliquota efetiva de IR/CSLL fora de 0-100% (-20.0%)");
        Value ev = IndicatorRules.company(Indicator.EV_EBIT, petrobrasFull(a));
        assertThat(ev.value()).isEqualByComparingTo("8.47");
        assertThat(ev.note()).isEqualTo("ON sem negociacao: avaliada pelo preco de PETR4");
    }

    @Test
    @DisplayName("Banco e seguradora: os 15 indicadores so do comercial nao se aplicam; listagem so com as 14 colunas")
    void applicabilityAndListing() {
        long commercialOnly = Arrays.stream(Indicator.values())
                .filter(i -> i.appliesTo(com.activest.domain.issuer.IssuerKind.COMPANY))
                .filter(i -> !i.appliesTo(com.activest.domain.issuer.IssuerKind.COMPANY, "BANCO")).count();
        assertThat(commercialOnly).isEqualTo(15);
        assertThat(Indicator.ROA.appliesTo(com.activest.domain.issuer.IssuerKind.COMPANY, "SEGURADORA")).isTrue();
        assertThat(Indicator.ROIC.appliesTo(com.activest.domain.issuer.IssuerKind.COMPANY, "SEGURADORA")).isFalse();
        assertThat(Indicator.ROIC.appliesTo(com.activest.domain.issuer.IssuerKind.COMPANY, null)).isTrue();
        assertThat(Arrays.stream(Indicator.values()).filter(Indicator::listed).map(Enum::name))
                .containsExactly("LPA", "VPA", "PL", "PVP", "ROE", "PAYOUT", "DY", "VP_COTA", "COTISTAS", "IMOVEIS",
                        "PAPEL", "ALAVANCAGEM");
        assertThat(Arrays.stream(Indicator.values()).filter(i -> i.appliesTo(com.activest.domain.issuer.IssuerKind.COMPANY)))
                .hasSize(30);
    }

    @Test
    @DisplayName("Media por janela (D14): os N ultimos anos fechados + Atual; MAX = todos; desde o 1o ano da janela")
    void windowAverages() {
        java.util.List<com.activest.domain.indicator.dto.IndicatorPointDTO> annual = new java.util.ArrayList<>();
        for (int y = 2018; y <= 2025; y++) {
            annual.add(new com.activest.domain.indicator.dto.IndicatorPointDTO(String.valueOf(y),
                    LocalDate.of(y, 12, 31), BigDecimal.valueOf(y - 2017), null));   // 1..8
        }
        var current = new com.activest.domain.indicator.dto.IndicatorPointDTO("Atual", LocalDate.of(2026, 6, 30),
                new BigDecimal("10"), null);
        var y3 = IndicatorService.windowAverage(IndicatorWindow.Y3, annual, current, Indicator.Unit.RATIO);
        assertThat(y3.window()).isEqualTo("3A");
        assertThat(y3.fromYear()).isEqualTo(2023);
        assertThat(y3.value()).isEqualByComparingTo("7.75");     // (6 + 7 + 8 + 10) / 4
        var y5 = IndicatorService.windowAverage(IndicatorWindow.Y5, annual, current, Indicator.Unit.RATIO);
        assertThat(y5.fromYear()).isEqualTo(2021);
        assertThat(y5.value()).isEqualByComparingTo("6.67");     // (4..8 + 10) / 6
        var max = IndicatorService.windowAverage(IndicatorWindow.MAX, annual, current, Indicator.Unit.RATIO);
        assertThat(max.fromYear()).isEqualTo(2018);
        assertThat(max.value()).isEqualByComparingTo("5.11");    // (1..8 + 10) / 9
        // menos anos que a janela: todos
        var few = IndicatorService.windowAverage(IndicatorWindow.Y5, annual.subList(0, 2), current, Indicator.Unit.RATIO);
        assertThat(few.fromYear()).isEqualTo(2018);
    }

    @Test
    @DisplayName("Media da empresa: nulos fora, zeros dentro")
    void media() {
        assertThat(IndicatorRules.average(Arrays.asList(new BigDecimal("10"), null, BigDecimal.ZERO,
                new BigDecimal("5")), Indicator.Unit.PERCENT)).isEqualByComparingTo("5.00");
        assertThat(IndicatorRules.average(Arrays.asList(null, null), Indicator.Unit.PERCENT)).isNull();
    }

    @Test
    @DisplayName("Rotulo do trimestre e fracionario")
    void labels() {
        assertThat(IndicatorService.quarterLabel(LocalDate.of(2026, 6, 30))).isEqualTo("2T26");
        assertThat(IndicatorService.quarterLabel(LocalDate.of(2025, 2, 28))).isEqualTo("1T25");
        assertThat(IndicatorService.baseTicker("PETR4F")).isEqualTo("PETR4");
        assertThat(IndicatorService.baseTicker("KLBN11F")).isEqualTo("KLBN11");
        assertThat(IndicatorService.baseTicker("HGLG11")).isEqualTo("HGLG11");
    }
}
