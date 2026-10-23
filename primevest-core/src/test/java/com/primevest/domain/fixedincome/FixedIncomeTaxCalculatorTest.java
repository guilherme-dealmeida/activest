package com.primevest.domain.fixedincome;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** IR e IOF de um lote (docs/planos/PLANO_VENDA_RENDA_FIXA.md, R6 e D3) e a precedencia da tabela. */
class FixedIncomeTaxCalculatorTest {

    private static FixedIncomeTaxRule rule(FixedIncomeTax tax, Long asset, int de, Integer ate, String rate,
                                            LocalDate validFrom, LocalDate validTo) {
        return FixedIncomeTaxRule.builder().tax(tax).assetId(asset).daysFrom(de).daysTo(ate)
            .rate(new BigDecimal(rate)).validFrom(validFrom).validTo(validTo).legalBasis("teste").build();
    }

    private static final FixedIncomeTaxTable TABLE = new FixedIncomeTaxTable(List.of(
        rule(FixedIncomeTax.IR, null, 0, 180, "0.225", null, null),
        rule(FixedIncomeTax.IR, null, 181, null, "0.20", null, null),
        rule(FixedIncomeTax.IOF, null, 0, 17, "0.43", null, null),
        rule(FixedIncomeTax.IOF, null, 18, null, "0", null, null),
        // isencao que acaba em 31/12/2026: depois disso vale a regra geral
        rule(FixedIncomeTax.IR, 7L, 0, null, "0", null, LocalDate.of(2026, 12, 31))
    ));

    @Test
    @DisplayName("IOF sai primeiro e o IR incide sobre o rendimento menos o IOF, ambos truncados")
    void iofBeforeIr() {
        FixedIncomeTaxTable.Rates r = TABLE.rates(null, LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 25));
        assertThat(r.days()).isEqualTo(17);
        // rendimento 24,63: IOF = 10,5909 -> 10,59; IR = (24,63 - 10,59) x 22,5% = 3,159 -> 3,15
        FixedIncomeTaxCalculator.Taxes t = FixedIncomeTaxCalculator.taxes(new BigDecimal("24.63"), r);
        assertThat(t.iof()).isEqualByComparingTo("10.59");
        assertThat(t.ir()).isEqualByComparingTo("3.15");
        assertThat(t.total()).isEqualByComparingTo("13.74");
        assertThat(r.onIncome()).isEqualByComparingTo("0.558250"); // 0,43 + 0,57 x 0,225
    }

    @Test
    @DisplayName("Rendimento zero ou negativo nao paga imposto")
    void withoutIncome() {
        FixedIncomeTaxTable.Rates r = TABLE.rates(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5));
        assertThat(FixedIncomeTaxCalculator.taxes(BigDecimal.ZERO, r).total()).isEqualByComparingTo("0");
        assertThat(FixedIncomeTaxCalculator.taxes(new BigDecimal("-3"), r).total()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Linha do produto vence a geral; vencida a isencao (pela data do resgate), volta a geral")
    void precedenceAndValidity() {
        LocalDate investment = LocalDate.of(2026, 6, 1);
        assertThat(TABLE.rates(7L, investment, LocalDate.of(2026, 12, 31)).ir()).isEqualByComparingTo("0");
        assertThat(TABLE.rates(7L, investment, LocalDate.of(2027, 1, 2)).ir()).isEqualByComparingTo("0.20");
        assertThat(TABLE.rates(8L, investment, LocalDate.of(2026, 7, 1)).ir()).isEqualByComparingTo("0.225");
    }

    @Test
    @DisplayName("Tabela vazia: tudo isento")
    void emptyTable() {
        FixedIncomeTaxTable.Rates r = FixedIncomeTaxTable.empty()
            .rates(null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2));
        assertThat(FixedIncomeTaxCalculator.taxes(new BigDecimal("100"), r).total()).isEqualByComparingTo("0");
    }
}
