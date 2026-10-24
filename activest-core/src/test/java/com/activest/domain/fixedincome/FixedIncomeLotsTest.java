package com.activest.domain.fixedincome;

import com.activest.domain.asset.Indexer;
import com.activest.domain.asset.RateType;
import com.activest.domain.calendar.BusinessCalendar;
import com.activest.domain.fixedincome.FixedIncomeLots.Move;
import com.activest.domain.fixedincome.FixedIncomeLots.Result;
import com.activest.domain.fixedincome.FixedIncomeLots.SaleOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Os lotes da renda fixa (docs/planos/PLANO_VENDA_RENDA_FIXA.md, V2). O gabarito principal e o extrato diario da
 * Caixinha Turbo do Nubank (RDB, 120% do CDI), em que a regra de saida e UEPS (secao 10 do plano).
 */
class FixedIncomeLotsTest {

    // ------------------------------------------------------------------ apoio

    /** As linhas gerais da V52 (sem as excecoes por produto). */
    static FixedIncomeTaxTable v52() {
        List<FixedIncomeTaxRule> r = new ArrayList<>();
        r.add(rule(FixedIncomeTax.IR, 0, 180, "0.225"));
        r.add(rule(FixedIncomeTax.IR, 181, 360, "0.20"));
        r.add(rule(FixedIncomeTax.IR, 361, 720, "0.175"));
        r.add(rule(FixedIncomeTax.IR, 721, null, "0.15"));
        int[] pct = {96, 93, 90, 86, 83, 80, 76, 73, 70, 66, 63, 60, 56, 53, 50, 46, 43, 40, 36, 33, 30, 26, 23, 20,
            16, 13, 10, 6, 3};
        for (int dayDate = 1; dayDate <= 29; dayDate++) {
            r.add(rule(FixedIncomeTax.IOF, dayDate == 1 ? 0 : dayDate, dayDate, BigDecimal.valueOf(pct[dayDate - 1], 2).toPlainString()));
        }
        r.add(rule(FixedIncomeTax.IOF, 30, null, "0"));
        return new FixedIncomeTaxTable(r);
    }

    private static FixedIncomeTaxRule rule(FixedIncomeTax tax, int de, Integer ate, String rate) {
        return FixedIncomeTaxRule.builder().tax(tax).daysFrom(de).daysTo(ate).rate(new BigDecimal(rate))
            .legalBasis("V52").build();
    }

    private static LocalDate d(int y, int m, int day) {
        return LocalDate.of(y, m, day);
    }

    /** CDI diario do periodo da caixinha (BCB, serie 12), constante entre as reunioes do Copom. */
    static NavigableMap<LocalDate, BigDecimal> nubankCdi() {
        NavigableMap<LocalDate, BigDecimal> m = new TreeMap<>();
        for (LocalDate x = d(2025, 7, 25); !x.isAfter(d(2026, 7, 27)); x = x.plusDays(1)) {
            if (!BusinessCalendar.isBusinessDay(x)) {
                continue;
            }
            String rateValue = !x.isAfter(d(2026, 3, 18)) ? "0.055131"
                : !x.isAfter(d(2026, 4, 29)) ? "0.054266"
                : !x.isAfter(d(2026, 6, 17)) ? "0.053400" : "0.052531";
            m.put(x, new BigDecimal(rateValue));
        }
        return m;
    }

    private static final FixedIncomeCurve.Terms SAVINGS_BOX =
        new FixedIncomeCurve.Terms(RateType.POS_FIXADO, Indexer.CDI, new BigDecimal("120"), d(2026, 7, 26));

    private static List<Move> nubankMovements(boolean withSettlement) {
        List<Move> m = new ArrayList<>(List.of(
            Move.buy("a1", d(2025, 7, 25), new BigDecimal("543.85")),
            Move.buy("a2", d(2025, 12, 29), new BigDecimal("9456.15")),
            Move.sellNet("r1", d(2026, 2, 9), new BigDecimal("8000.00"), false),
            Move.buy("a3", d(2026, 5, 14), new BigDecimal("2000.00")),
            Move.sellNet("r2", d(2026, 7, 1), new BigDecimal("3000.00"), false),
            Move.buy("a4", d(2026, 7, 8), new BigDecimal("3000.00"))));
        if (withSettlement) {
            m.add(Move.sellNet("liq", d(2026, 7, 26), new BigDecimal("4356.61"), true));
        }
        return m;
    }

    private static Result nubank(SaleRule taxRule, LocalDate ref, boolean withSettlement) {
        return FixedIncomeLots.run(SAVINGS_BOX, nubankMovements(withSettlement), ref, nubankCdi(), null, v52(), null,
            taxRule);
    }

    // ------------------------------------------------------------------ Nubank (UEPS, o real)

    @Test
    @DisplayName("Nubank em UEPS: os dois resgates batem com o extrato (bruto e IR)")
    void nubankLifoRedemptions() {
        Result r = nubank(SaleRule.UEPS, d(2026, 7, 25), false);
        SaleOutcome r1 = r.sale("r1");
        assertThat(r1.gross().doubleValue()).isCloseTo(8034.34, within(0.02));
        assertThat(r1.ir().doubleValue()).isCloseTo(34.34, within(0.02));
        assertThat(r1.iof()).isEqualByComparingTo("0");
        assertThat(r1.net()).isEqualByComparingTo("8000.00");
        SaleOutcome r2 = r.sale("r2");
        assertThat(r2.gross().doubleValue()).isCloseTo(3024.93, within(0.02));
        assertThat(r2.ir().doubleValue()).isCloseTo(24.93, within(0.02));
    }

    @Test
    @DisplayName("Nubank em UEPS: em 25/07/2026 o saldo, o imposto e o valor investido batem com o extrato")
    void nubankLifoBalance() {
        Result r = nubank(SaleRule.UEPS, d(2026, 7, 25), false);
        assertThat(r.status()).isEqualTo(FixedIncomeCurve.Status.OK);
        assertThat(r.balance().doubleValue()).isCloseTo(4399.86, within(0.02));
        BigDecimal taxes = r.balance().subtract(r.estimatedNet());
        assertThat(taxes.doubleValue()).isCloseTo(43.82, within(0.03));
        assertThat(r.applied().doubleValue()).isCloseTo(4212.71, within(0.05));
        // tres lotes vivos: 25/07/2025 (17,5%), o resto de 29/12/2025 (20%) e 08/07/2026 (IOF de 17 dias)
        assertThat(r.lots()).hasSize(3);
        assertThat(r.lots().get(0).irRate()).isEqualByComparingTo("0.175");
        assertThat(r.lots().get(2).days()).isEqualTo(17);
        assertThat(r.lots().get(2).iofRate()).isEqualByComparingTo("0.43");
    }

    @Test
    @DisplayName("Nubank: a liquidacao total no vencimento zera os lotes e o ajuste e de centavos")
    void nubankSettlement() {
        Result r = nubank(SaleRule.UEPS, d(2026, 7, 27), true);
        assertThat(r.lots()).isEmpty();
        assertThat(r.balance()).isEqualByComparingTo("0");
        SaleOutcome liq = r.sale("liq");
        assertThat(liq.net()).isEqualByComparingTo("4356.61");
        assertThat(liq.adjustment().abs().doubleValue()).isLessThan(1.0);
        assertThat(liq.gross().subtract(liq.iof()).subtract(liq.ir())).isEqualByComparingTo("4356.61");
    }

    // ------------------------------------------------------------------ as outras regras

    @Test
    @DisplayName("Nubank em PROPORCIONAL e PEPS: os brutos do levantamento (secao 10 do plano)")
    void nubankOtherRules() {
        Result p = nubank(SaleRule.PROPORCIONAL, d(2026, 7, 25), false);
        assertThat(p.sale("r1").gross().doubleValue()).isCloseTo(8040.53, within(0.03));
        assertThat(p.sale("r2").gross().doubleValue()).isCloseTo(3033.14, within(0.03));
        Result f = nubank(SaleRule.PEPS, d(2026, 7, 25), false);
        assertThat(f.sale("r1").gross().doubleValue()).isCloseTo(8042.22, within(0.03));
        assertThat(f.sale("r2").gross().doubleValue()).isCloseTo(3039.80, within(0.03));
    }

    @Test
    @DisplayName("PROPORCIONAL fica entre PEPS e UEPS no aplicado restante")
    void proportionalBetweenBoth() {
        BigDecimal lifo = nubank(SaleRule.UEPS, d(2026, 7, 25), false).applied();
        BigDecimal prop = nubank(SaleRule.PROPORCIONAL, d(2026, 7, 25), false).applied();
        BigDecimal peps = nubank(SaleRule.PEPS, d(2026, 7, 25), false).applied();
        assertThat(prop).isGreaterThan(lifo).isLessThan(peps);
    }

    // ------------------------------------------------------------------ regressao contra a curva

    @Test
    @DisplayName("A soma dos lotes e o saldo da curva com os mesmos brutos")
    void lotsMatchCurve() {
        Result r = nubank(SaleRule.UEPS, d(2026, 7, 25), false);
        List<FixedIncomeCurve.Flow> flows = new ArrayList<>();
        for (Move m : nubankMovements(false)) {
            BigDecimal v = m.kind() == FixedIncomeLots.Kind.BUY ? m.amount() : r.sale(m.key()).gross().negate();
            flows.add(new FixedIncomeCurve.Flow(m.date(), v));
        }
        FixedIncomeCurve.Result curve = FixedIncomeCurve.value(SAVINGS_BOX, flows, d(2026, 7, 25), nubankCdi(), null);
        assertThat(r.balance().doubleValue()).isCloseTo(curve.balance().doubleValue(), within(0.01));
    }

    // ------------------------------------------------------------------ casos a mao

    private static final FixedIncomeCurve.Terms PRE_10 =
        new FixedIncomeCurve.Terms(RateType.PREFIXADO, null, new BigDecimal("10"), null);

    @Test
    @DisplayName("Venda pelo bruto: o liquido e o bruto menos os impostos; sem rendimento, sem imposto")
    void saleByGross() {
        Result r = FixedIncomeLots.run(PRE_10, List.of(
                Move.buy("c", d(2026, 1, 5), new BigDecimal("1000")),
                Move.sellGross("v", d(2026, 1, 5), new BigDecimal("400"), false)),
            d(2026, 1, 5), null, null, v52(), null, SaleRule.PROPORCIONAL);
        SaleOutcome v = r.sale("v");
        assertThat(v.gross()).isEqualByComparingTo("400.00");
        assertThat(v.net()).isEqualByComparingTo("400.00");
        assertThat(r.applied()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("Acima do disponivel (D4, PENDENCIAS 27.5): o excedente tambem paga IR; o titulo cai para o centavo "
        + "da regra F e a venda fica marcada")
    void aboveAvailableCase() {
        Result r = FixedIncomeLots.run(PRE_10, List.of(
                Move.buy("c", d(2026, 1, 5), new BigDecimal("1000")),
                Move.sellNet("v", d(2026, 6, 1), new BigDecimal("1100"), false)),
            d(2026, 6, 1), null, null, v52(), null, SaleRule.PROPORCIONAL);
        SaleOutcome v = r.sale("v");
        assertThat(v.aboveAvailable()).isTrue();
        assertThat(r.balance()).isEqualByComparingTo("0.01");
        // 147 dias, IR de 22,5% sobre todo o rendimento: bruto - 22,5% x (bruto - 1000) = 1100 -> bruto = 1129,03
        assertThat(v.gross().doubleValue()).isCloseTo(1129.03, within(0.02));
        assertThat(v.ir().doubleValue()).isCloseTo(29.03, within(0.02));
        assertThat(v.gross()).isEqualByComparingTo(v.net().add(v.ir()).add(v.iof()));
        assertThat(v.adjustment()).isEqualByComparingTo(v.gross().subtract(v.grossBefore()));
    }

    @Test
    @DisplayName("Produto isento (tabela vazia): bruto = liquido")
    void exempt() {
        Result r = FixedIncomeLots.run(PRE_10, List.of(
                Move.buy("c", d(2026, 1, 5), new BigDecimal("1000")),
                Move.sellNet("v", d(2026, 6, 1), new BigDecimal("500"), false)),
            d(2026, 6, 1), null, null, FixedIncomeTaxTable.empty(), null, SaleRule.PROPORCIONAL);
        assertThat(r.sale("v").gross()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Sem curva (TR): nao rende, sem imposto, bruto = liquido e status UNSUPPORTED")
    void withoutCurve() {
        FixedIncomeCurve.Terms tr = new FixedIncomeCurve.Terms(RateType.POS_FIXADO, Indexer.TR, new BigDecimal("100"),
            null);
        Result r = FixedIncomeLots.run(tr, List.of(
                Move.buy("c", d(2026, 1, 5), new BigDecimal("1000")),
                Move.sellNet("v", d(2026, 6, 1), new BigDecimal("300"), false)),
            d(2026, 7, 1), null, null, v52(), null, SaleRule.PROPORCIONAL);
        assertThat(r.status()).isEqualTo(FixedIncomeCurve.Status.UNSUPPORTED);
        assertThat(r.sale("v").gross()).isEqualByComparingTo("300.00");
        assertThat(r.balance()).isEqualByComparingTo("700.00");
    }
}
