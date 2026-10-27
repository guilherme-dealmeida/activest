package com.activest.domain.currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Fórmulas dos tributos do exterior (PLANO_CADASTRO_MOEDAS, C5–C7, D20, D23). */
class ForeignTaxCalculatorTest {

    private static ForeignTaxRule rule(String rate, String brackets) {
        return ForeignTaxRule.builder().id(1L).rate(new BigDecimal(rate)).brackets(brackets).build();
    }

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    @DisplayName("Alíquota simples: IOF 1,1% e retenção de 30%; base zero ou negativa = 0")
    void flat() {
        assertThat(ForeignTaxCalculator.flat(d("10000"), rule("0.011", null))).isEqualByComparingTo("110.00");
        assertThat(ForeignTaxCalculator.flat(d("53.37"), rule("0.30", null))).isEqualByComparingTo("16.01");
        assertThat(ForeignTaxCalculator.flat(d("-1"), rule("0.30", null))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("IR do provento: 15% (ou 27,5%) menos os 30% retidos nunca fica negativo")
    void dividend() {
        assertThat(ForeignTaxCalculator.dividendIncomeTax(d("100"), d("30"), rule("0.15", null))).isEqualByComparingTo("0");
        assertThat(ForeignTaxCalculator.dividendIncomeTax(d("100"), d("30"), rule("0.275", null))).isEqualByComparingTo("0");
        assertThat(ForeignTaxCalculator.dividendIncomeTax(d("100"), d("10"), rule("0.15", null))).isEqualByComparingTo("5.00");
        assertThat(ForeignTaxCalculator.dividendIncomeTax(d("100"), null, rule("0.15", null))).isEqualByComparingTo("15.00");
    }

    @Test
    @DisplayName("IR da venda desde 2024: 15% sobre o ganho; perda = 0")
    void saleFlat() {
        assertThat(ForeignTaxCalculator.saleIncomeTax(d("1000"), d("50000"), rule("0.15", null)))
            .isEqualByComparingTo("150.00");
        assertThat(ForeignTaxCalculator.saleIncomeTax(d("-10"), d("50000"), rule("0.15", null))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Regime anterior: isenção com vendas do mês até R$ 35 mil e faixas progressivas por parcela")
    void salePrevious() {
        String json = "{\"monthlyExemption\": 35000, \"brackets\": [{\"upTo\": 5000000, \"rate\": 0.15},"
            + " {\"upTo\": 10000000, \"rate\": 0.175}, {\"upTo\": 30000000, \"rate\": 0.20}, {\"upTo\": null, \"rate\": 0.225}]}";
        assertThat(ForeignTaxCalculator.saleIncomeTax(d("5000"), d("35000"), rule("0.15", json))).isEqualByComparingTo("0");
        assertThat(ForeignTaxCalculator.saleIncomeTax(d("5000"), d("35000.01"), rule("0.15", json)))
            .isEqualByComparingTo("750.00");
        assertThat(ForeignTaxCalculator.saleIncomeTax(d("6000000"), d("9000000"), rule("0.15", json)))
            .isEqualByComparingTo("925000.00");
        assertThat(ForeignTaxCalculator.saleIncomeTax(d("5000"), d("40000"), rule("0.15", "{\"monthlyExemption\": 35000}")))
            .isEqualByComparingTo("750.00");
    }
}
