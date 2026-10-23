package com.activest.domain.dividend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static com.activest.domain.dividend.DividendType.*;
import static com.activest.domain.dividend.TaxBaseDate.CREDIT;
import static com.activest.domain.dividend.TaxBaseDate.PAYMENT;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * O jeito de calcular o IR retido (docs/planos/PLANO_IR_PROVENTOS.md, 5.1). Os valores sao
 * os do extrato da B3 conferido em 24/09/2026.
 */
class WithholdingTaxCalculatorTest {

    private static final long STOCK = 1L;
    private static final long FII = 2L;
    private static final long ETF = 5L;
    private static final long BDR = 7L;

    /** A carga da V23, em memoria. */
    private static final List<WithholdingTaxRule> RULES = List.of(
        taxRule(1, JCP, STOCK, "0.1500", CREDIT, null, "2025-12-31"),
        taxRule(2, JCP, STOCK, "0.1750", CREDIT, "2026-01-01", null),
        taxRule(3, DIVIDENDO, STOCK, "0.0000", PAYMENT, null, null),
        taxRule(4, RENDIMENTO, STOCK, "0.2250", PAYMENT, null, null),
        taxRule(5, RESTITUICAO_CAPITAL, STOCK, "0.0000", PAYMENT, null, null),
        taxRule(6, RENDIMENTO, FII, "0.0000", PAYMENT, null, null),
        taxRule(7, AMORTIZACAO, FII, "0.0000", PAYMENT, null, null));

    private static WithholdingTaxRule taxRule(long id, DividendType type, long asset, String aliquota, TaxBaseDate base,
                                            String de, String ate) {
        return WithholdingTaxRule.builder().id(id).dividendType(type).assetTypeId(asset)
            .rate(new BigDecimal(aliquota)).baseDate(base)
            .validFrom(de == null ? null : LocalDate.parse(de)).validTo(ate == null ? null : LocalDate.parse(ate))
            .legalBasis("teste").build();
    }

    private static WithholdingTaxCalculator.Input dividendEvent(DividendType type, Long asset, String approval,
                                                          String dataCom, String payment, String value, String shares) {
        return new WithholdingTaxCalculator.Input(type, asset,
            approval == null ? null : LocalDate.parse(approval),
            dataCom == null ? null : LocalDate.parse(dataCom),
            payment == null ? null : LocalDate.parse(payment),
            new BigDecimal(value), new BigDecimal(shares));
    }

    private static WithholdingTaxCalculator.Result compute(WithholdingTaxCalculator.Input in) {
        return WithholdingTaxCalculator.calculate(RULES, in);
    }

    @Test
    @DisplayName("Conta da B3 — BBAS3 JCP de 11/06/2026, 125 cotas: 8,41 + 6,16 = 14,57")
    void b3AccountBbas3() {
        var p1 = compute(dividendEvent(JCP, STOCK, "2026-05-14", "2026-06-01", "2026-06-11", "0.08157785203", "125"));
        var p2 = compute(dividendEvent(JCP, STOCK, "2026-05-14", "2026-06-01", "2026-06-11", "0.05968401166", "125"));

        assertThat(p1.gross()).isEqualByComparingTo("10.19");       // 10,1972: truncado, nao 10,20
        assertThat(p1.tax()).isEqualByComparingTo("1.78");           // 1,7846: truncado
        assertThat(p1.net()).isEqualByComparingTo("8.41");
        assertThat(p2.gross()).isEqualByComparingTo("7.46");
        assertThat(p2.tax()).isEqualByComparingTo("1.30");
        assertThat(p2.net()).isEqualByComparingTo("6.16");
        assertThat(p1.net().add(p2.net())).isEqualByComparingTo("14.57");
        assertThat(p1.rate()).isEqualByComparingTo("0.175");
    }

    @Test
    @DisplayName("TOTAL e bruto - IR, nunca liquido por cota x cotas (que daria 6,15 e 20,04)")
    void totalDoesNotComeFromNetPerShare() {
        var bbas = compute(dividendEvent(JCP, STOCK, "2026-05-14", "2026-06-01", "2026-06-11", "0.05968401166", "125"));
        var petr = compute(dividendEvent(JCP, STOCK, "2025-12-11", "2025-12-22", "2026-02-20", "0.47160378", "50"));

        assertThat(bbas.netPerShare()).isEqualByComparingTo("0.04923931");
        assertThat(bbas.netPerShare().multiply(new BigDecimal("125")).setScale(2, RoundingMode.DOWN))
            .isEqualByComparingTo("6.15");
        assertThat(bbas.net()).isEqualByComparingTo("6.16");

        assertThat(petr.netPerShare()).isEqualByComparingTo("0.40086321");
        assertThat(petr.netPerShare().multiply(new BigDecimal("50")).setScale(2, RoundingMode.DOWN))
            .isEqualByComparingTo("20.04");
        assertThat(petr.net()).isEqualByComparingTo("20.05");
    }

    @Test
    @DisplayName("Liquido por cota tem 8 casas")
    void netPerShareWithEightDecimals() {
        var r = compute(dividendEvent(JCP, STOCK, "2026-04-16", "2026-04-22", "2026-06-22", "0.31311454", "50"));

        assertThat(r.netPerShare().scale()).isEqualTo(8);
        assertThat(r.netPerShare()).isEqualByComparingTo("0.25831950");
    }

    @Test
    @DisplayName("JCP creditado em 2025 e pago em 2026 retem 15% (PETR4 20/02/2026 = 20,05)")
    void jcpCreditedIn2025() {
        var r = compute(dividendEvent(JCP, STOCK, "2025-12-11", "2025-12-22", "2026-02-20", "0.47160378", "50"));

        assertThat(r.rate()).isEqualByComparingTo("0.15");
        assertThat(r.gross()).isEqualByComparingTo("23.58");
        assertThat(r.tax()).isEqualByComparingTo("3.53");
        assertThat(r.net()).isEqualByComparingTo("20.05");
    }

    @Test
    @DisplayName("Bruto truncado: PETR4 20/03/2026 = 8,75 (nao 8,76) e liquido 7,44")
    void truncatedGross() {
        var r = compute(dividendEvent(JCP, STOCK, "2025-12-11", "2025-12-22", "2026-03-20", "0.17518233", "50"));

        assertThat(r.gross()).isEqualByComparingTo("8.75");
        assertThat(r.tax()).isEqualByComparingTo("1.31");
        assertThat(r.net()).isEqualByComparingTo("7.44");
    }

    @Test
    @DisplayName("Fronteira da vigencia: credito em 31/12/2025 retem 15%; em 01/01/2026, 17,5%")
    void validityBoundary() {
        var lastDay = compute(dividendEvent(JCP, STOCK, "2025-12-31", "2026-01-05", "2026-03-01", "1", "100"));
        var firstDay = compute(dividendEvent(JCP, STOCK, "2026-01-01", "2026-01-05", "2026-03-01", "1", "100"));

        assertThat(lastDay.rule().getId()).isEqualTo(1L);
        assertThat(lastDay.rate()).isEqualByComparingTo("0.15");
        assertThat(firstDay.rule().getId()).isEqualTo(2L);
        assertThat(firstDay.rate()).isEqualByComparingTo("0.175");
    }

    @Test
    @DisplayName("Data de credito: aprovacao; sem ela, data-com; sem as duas, pagamento")
    void creditDate() {
        var withoutApproval = compute(dividendEvent(JCP, STOCK, null, "2025-12-22", "2026-02-20", "1", "100"));
        var onlyPayment = compute(dividendEvent(JCP, STOCK, null, null, "2026-02-20", "1", "100"));

        assertThat(withoutApproval.rate()).isEqualByComparingTo("0.15");
        assertThat(onlyPayment.rate()).isEqualByComparingTo("0.175");
    }

    @Test
    @DisplayName("Regra com base no pagamento usa o pagamento; sem ele, a data-com")
    void baseOnPayment() {
        List<WithholdingTaxRule> rules = List.of(
            taxRule(10, RENDIMENTO, STOCK, "0.2000", PAYMENT, null, "2026-06-30"),
            taxRule(11, RENDIMENTO, STOCK, "0.2250", PAYMENT, "2026-07-01", null));

        var paidAfter = WithholdingTaxCalculator.calculate(rules,
            dividendEvent(RENDIMENTO, STOCK, "2026-06-01", "2026-06-10", "2026-07-15", "1", "100"));
        var withoutPayment = WithholdingTaxCalculator.calculate(rules,
            dividendEvent(RENDIMENTO, STOCK, "2026-06-01", "2026-06-10", null, "1", "100"));

        assertThat(paidAfter.rule().getId()).isEqualTo(11L);
        assertThat(withoutPayment.rule().getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Rendimento de acao (atualizacao pela Selic) retem 22,5%: PETR4 22/06/2026 = 0,79")
    void stockIncome() {
        var r = compute(dividendEvent(RENDIMENTO, STOCK, "2026-04-16", "2026-04-22", "2026-06-22", "0.02038398", "50"));

        assertThat(r.gross()).isEqualByComparingTo("1.01");
        assertThat(r.tax()).isEqualByComparingTo("0.22");
        assertThat(r.net()).isEqualByComparingTo("0.79");
    }

    @Test
    @DisplayName("Isento: aliquota zero, IR zero e TOTAL igual ao bruto (dividendo PETR4 = 14,82)")
    void exempt() {
        var r = compute(dividendEvent(DIVIDENDO, STOCK, "2025-12-11", "2025-12-22", "2026-03-20", "0.29642144", "50"));

        assertThat(r.hasRule()).isTrue();
        assertThat(r.rate()).isEqualByComparingTo("0");
        assertThat(r.tax()).isEqualByComparingTo("0");
        assertThat(r.net()).isEqualByComparingTo("14.82");
        assertThat(r.netPerShare()).isEqualByComparingTo("0.29642144");
    }

    @Test
    @DisplayName("Rendimento de FII, amortizacao e restituicao de capital sao isentos")
    void otherExempt() {
        assertThat(compute(dividendEvent(RENDIMENTO, FII, null, "2026-08-29", "2026-09-15", "1.10", "10")).rate())
            .isEqualByComparingTo("0");
        assertThat(compute(dividendEvent(AMORTIZACAO, FII, null, "2026-06-30", "2026-07-15", "1.00", "100")).net())
            .isEqualByComparingTo("100.00");
        assertThat(compute(dividendEvent(RESTITUICAO_CAPITAL, STOCK, null, "2026-06-30", "2026-07-15", "0.50", "10")).net())
            .isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("Sem regra (ETF; dividendo de BDR): aliquota, IR e liquidos nulos; bruto preenchido")
    void withoutRule() {
        var etf = compute(dividendEvent(RENDIMENTO, ETF, "2026-09-04", "2026-09-04", "2026-09-15", "0.79830000000", "10"));
        var bdr = compute(dividendEvent(DIVIDENDO, BDR, null, "2026-08-01", "2026-08-20", "0.50", "10"));

        for (var r : List.of(etf, bdr)) {
            assertThat(r.hasRule()).isFalse();
            assertThat(r.rate()).isNull();
            assertThat(r.tax()).isNull();
            assertThat(r.netPerShare()).isNull();
            assertThat(r.net()).isNull();
        }
        assertThat(etf.gross()).isEqualByComparingTo("7.98");
    }

    @Test
    @DisplayName("Data fora de toda vigencia, tipo nulo ou tipo de ativo nulo: sem regra")
    void outOfValidity() {
        List<WithholdingTaxRule> so2025 = List.of(taxRule(1, JCP, STOCK, "0.1500", CREDIT, null, "2025-12-31"));

        assertThat(WithholdingTaxCalculator.calculate(so2025,
            dividendEvent(JCP, STOCK, "2026-04-16", "2026-04-22", "2026-06-22", "1", "10")).hasRule()).isFalse();
        assertThat(compute(dividendEvent(null, STOCK, null, "2026-04-22", null, "1", "10")).hasRule()).isFalse();
        assertThat(compute(dividendEvent(JCP, null, "2026-04-16", "2026-04-22", null, "1", "10")).hasRule()).isFalse();
    }
}
