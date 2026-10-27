package com.activest.domain.fund;

import com.activest.domain.fixedincome.FixedIncomeTax;
import com.activest.domain.fixedincome.FixedIncomeTaxRule;
import com.activest.domain.fixedincome.TaxRegime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Come-cotas e imposto do resgate de fundo (PLANO_FUNDOS_INVESTIMENTO.md, C7–C9). */
class FundTaxTest {

    private static FixedIncomeTaxRule rule(FixedIncomeTax tax, TaxRegime regime, int from, Integer to, String rate) {
        return FixedIncomeTaxRule.builder().tax(tax).regime(regime).daysFrom(from).daysTo(to)
            .rate(new BigDecimal(rate)).legalBasis("teste").build();
    }

    private static final List<FixedIncomeTaxRule> RULES = List.of(
        rule(FixedIncomeTax.IOF, TaxRegime.GENERAL, 5, 5, "0.83"),
        rule(FixedIncomeTax.IR, TaxRegime.GENERAL, 0, null, "0.99"),
        rule(FixedIncomeTax.IR, TaxRegime.FUND_LONG_TERM, 0, 180, "0.225"),
        rule(FixedIncomeTax.IR, TaxRegime.FUND_LONG_TERM, 181, 360, "0.20"),
        rule(FixedIncomeTax.IR, TaxRegime.FUND_LONG_TERM, 361, 720, "0.175"),
        rule(FixedIncomeTax.IR, TaxRegime.FUND_LONG_TERM, 721, null, "0.15"),
        rule(FixedIncomeTax.CC, TaxRegime.FUND_LONG_TERM, 0, null, "0.15"),
        rule(FixedIncomeTax.IR, TaxRegime.FUND_SHORT_TERM, 0, 180, "0.225"),
        rule(FixedIncomeTax.IR, TaxRegime.FUND_SHORT_TERM, 181, null, "0.20"),
        rule(FixedIncomeTax.CC, TaxRegime.FUND_SHORT_TERM, 0, null, "0.20"),
        rule(FixedIncomeTax.IR, TaxRegime.FUND_EQUITY, 0, null, "0.15"));

    private static final UUID BUY = UUID.randomUUID();
    private static final UUID SELL = UUID.randomUUID();

    @Test
    @DisplayName("Regime: Previdencia sem imposto, Acoes 15%, demais pela tributacao de longo prazo")
    void regime() {
        assertThat(FundTax.regime("Previdência", false)).isNull();
        assertThat(FundTax.regime("Ações", true)).isEqualTo(TaxRegime.FUND_EQUITY);
        assertThat(FundTax.regime("Renda Fixa", true)).isEqualTo(TaxRegime.FUND_LONG_TERM);
        assertThat(FundTax.regime("Multimercado", false)).isEqualTo(TaxRegime.FUND_SHORT_TERM);
        assertThat(FundTax.regime("Renda Fixa", null)).isEqualTo(TaxRegime.FUND_LONG_TERM);
    }

    @Test
    @DisplayName("Datas do come-cotas: ultimo dia util de maio e de novembro")
    void dates() {
        assertThat(FundTax.comeCotasDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
            .containsExactly(LocalDate.of(2024, 5, 31), LocalDate.of(2024, 11, 29));
    }

    @Test
    @DisplayName("Longo prazo: dois come-cotas de 15% em cotas e o IR do resgate descontando o ja retido")
    void longTerm() {
        Map<LocalDate, BigDecimal> quotes = Map.of(LocalDate.of(2024, 5, 31), new BigDecimal("1.10"),
            LocalDate.of(2024, 11, 29), new BigDecimal("1.20"));
        FundTax.Result r = FundTax.calculate(TaxRegime.FUND_LONG_TERM, RULES, List.of(
                new FundTax.Tx(BUY, true, LocalDate.of(2024, 1, 10), new BigDecimal("1000"), BigDecimal.ONE,
                    new BigDecimal("1000.00")),
                new FundTax.Tx(SELL, false, LocalDate.of(2025, 3, 10), new BigDecimal("974.03863636"),
                    new BigDecimal("1.30"), new BigDecimal("1266.25"))),
            quotes::get, LocalDate.of(2025, 3, 10));

        assertThat(r.comeCotas()).hasSize(2);
        assertThat(r.comeCotas().get(0).tax()).isEqualByComparingTo("15.00");
        assertThat(r.comeCotas().get(0).sharesWithheld()).isEqualByComparingTo("13.63636364");
        assertThat(r.comeCotas().get(1).tax()).isEqualByComparingTo("14.79");
        assertThat(r.comeCotas().get(1).sharesWithheld()).isEqualByComparingTo("12.32500000");
        FundTax.SaleTax s = r.sales().get(SELL);
        assertThat(s.iof()).isEqualByComparingTo("0");
        // 292,21 de ganho x 17,5% (425 dias) - 29,79 de come-cotas
        assertThat(s.incomeTax()).isEqualByComparingTo("21.34");
        assertThat(s.net()).isEqualByComparingTo("1244.91");
    }

    @Test
    @DisplayName("Curto prazo com IOF: resgate em 5 dias paga IOF e IR sobre o que sobra")
    void shortTermIof() {
        FundTax.Result r = FundTax.calculate(TaxRegime.FUND_SHORT_TERM, RULES, List.of(
                new FundTax.Tx(BUY, true, LocalDate.of(2025, 1, 6), new BigDecimal("100"), BigDecimal.TEN, null),
                new FundTax.Tx(SELL, false, LocalDate.of(2025, 1, 11), new BigDecimal("100"), new BigDecimal("11"),
                    new BigDecimal("1100.00"))),
            d -> null, LocalDate.of(2025, 1, 11));
        assertThat(r.sales().get(SELL).iof()).isEqualByComparingTo("83.00");
        assertThat(r.sales().get(SELL).incomeTax()).isEqualByComparingTo("3.82");
    }

    @Test
    @DisplayName("Acoes: 15% no resgate, sem come-cotas e sem IOF; Previdencia: bruto")
    void equityAndPension() {
        List<FundTax.Tx> txs = List.of(
            new FundTax.Tx(BUY, true, LocalDate.of(2024, 1, 10), new BigDecimal("100"), BigDecimal.TEN, null),
            new FundTax.Tx(SELL, false, LocalDate.of(2025, 1, 15), new BigDecimal("100"), new BigDecimal("12"),
                new BigDecimal("1200.00")));
        FundTax.Result equity = FundTax.calculate(TaxRegime.FUND_EQUITY, RULES, txs, d -> new BigDecimal("11"),
            LocalDate.of(2025, 1, 15));
        assertThat(equity.comeCotas()).isEmpty();
        assertThat(equity.sales().get(SELL).incomeTax()).isEqualByComparingTo("30.00");
        assertThat(equity.sales().get(SELL).iof()).isZero();
        FundTax.Result pension = FundTax.calculate(null, RULES, txs, d -> new BigDecimal("11"),
            LocalDate.of(2025, 1, 15));
        assertThat(pension.comeCotas()).isEmpty();
        assertThat(pension.sales().get(SELL).net()).isEqualByComparingTo("1200.00");
    }

    @Test
    @DisplayName("Cota abaixo da base: sem come-cotas (compensacao de prejuizo fora)")
    void noGainNoWithholding() {
        FundTax.Result r = FundTax.calculate(TaxRegime.FUND_LONG_TERM, RULES, List.of(
                new FundTax.Tx(BUY, true, LocalDate.of(2024, 1, 10), new BigDecimal("100"), BigDecimal.TEN, null)),
            d -> new BigDecimal("9"), LocalDate.of(2024, 12, 31));
        assertThat(r.comeCotas()).isEmpty();
    }

    @Test
    @DisplayName("Liquido estimado (C10): resgate total a cota atual, IR pelo prazo de cada lote; Previdencia = bruto")
    void estimate() {
        List<FundTax.Tx> txs = List.of(
            new FundTax.Tx(BUY, true, LocalDate.of(2026, 1, 5), new BigDecimal("100"), new BigDecimal("10"),
                new BigDecimal("1000.00")));
        FundTax.Result r = FundTax.calculate(TaxRegime.FUND_LONG_TERM, RULES, txs, d -> null, LocalDate.of(2026, 3, 1));
        assertThat(r.openLots()).hasSize(1);
        FundTax.SaleTax est = FundTax.estimate(TaxRegime.FUND_LONG_TERM, RULES, r.openLots(), new BigDecimal("12"),
            LocalDate.of(2026, 3, 1));
        assertThat(est.gross()).isEqualByComparingTo("1200.00");
        assertThat(est.iof()).isEqualByComparingTo("0");
        assertThat(est.incomeTax()).isEqualByComparingTo("45.00");
        assertThat(est.net()).isEqualByComparingTo("1155.00");

        FundTax.SaleTax pension = FundTax.estimate(null, RULES, r.openLots(), new BigDecimal("12"),
            LocalDate.of(2026, 3, 1));
        assertThat(pension.net()).isEqualByComparingTo("1200.00");
    }
}
