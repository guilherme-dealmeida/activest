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
