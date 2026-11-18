package com.activest.domain.portfolio;

import com.activest.domain.corporateaction.StockEventStep;
import com.activest.domain.corporateaction.StockEventType;
import com.activest.domain.transaction.Transaction;
import com.activest.domain.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os eventos do papel que mexeram na posicao, como linhas do extrato de Transacoes
 * (docs/planos/PLANO_EVENTOS_TRANSACOES.md): o mesmo percurso do calculo da posicao, com o antes e o depois.
 */
class PositionCalculatorAppliedEventsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 18);

    private final PositionCalculator calculator = new PositionCalculator(null, null, null);

    private static Transaction tx(TransactionType type, String date, String shares, String price, int order) {
        BigDecimal qty = new BigDecimal(shares);
        BigDecimal unit = new BigDecimal(price);
        return Transaction.builder()
            .type(type)
            .shares(qty)
            .pricePerShare(unit)
            .totalAmount(qty.multiply(unit))
            .transactionDate(LocalDate.parse(date))
            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(order))
            .build();
    }

    private static StockEventStep event(long id, StockEventType type, String dataCom, String factor, String cost) {
        return new StockEventStep(LocalDate.parse(dataCom), type, new BigDecimal(factor),
            cost == null ? null : new BigDecimal(cost), id);
    }

    private List<AppliedStockEvent> applied(List<Transaction> txs, List<StockEventStep> events) {
        return calculator.appliedStockEvents(txs, PositionEvents.ofStockEvents(events), TODAY);
    }

    private Position position(List<Transaction> txs, List<StockEventStep> events) {
        return calculator.calculate(false, txs, PositionEvents.ofStockEvents(events), TODAY);
    }

    /** Soma das quantidades do extrato: compras, menos vendas, mais a variacao de cada evento. */
    private static BigDecimal ledgerShares(List<Transaction> txs, List<AppliedStockEvent> events) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Transaction t : txs) {
            sum = t.getType() == TransactionType.BUY ? sum.add(t.getShares()) : sum.subtract(t.getShares());
        }
        for (AppliedStockEvent e : events) {
            sum = sum.add(e.sharesDelta());
        }
        return sum;
    }

    @Test
    @DisplayName("KLBN4 (caso real): bonificacao de 1% sobre 300 cotas = +3 sem custo (corretora: R$ 3,46); o IR soma R$ 38,84 (R$ 3,53)")
    void klbn4Bonus() {
        List<Transaction> txs = List.of(
            tx(TransactionType.BUY, "2025-10-03", "100", "3.62", 1),
            tx(TransactionType.BUY, "2025-10-06", "100", "3.58", 2),
            tx(TransactionType.BUY, "2025-10-10", "100", "3.49", 3),
            tx(TransactionType.BUY, "2026-05-06", "147", "3.42", 4),
            tx(TransactionType.BUY, "2026-05-14", "150", "3.38", 5));
        List<StockEventStep> events = List.of(
            event(621, StockEventType.BONIFICACAO, "2025-12-17", "1", "12.94564989"));

        List<AppliedStockEvent> out = applied(txs, events);

        assertThat(out).hasSize(1);
        AppliedStockEvent e = out.get(0);
        assertThat(e.eventId()).isEqualTo(621L);
        assertThat(e.sharesBefore()).isEqualByComparingTo("300");
        assertThat(e.sharesAfter()).isEqualByComparingTo("303");
        assertThat(e.sharesDelta()).isEqualByComparingTo("3");
        assertThat(e.taxUnitCost()).isEqualByComparingTo("12.94564989");
        assertThat(e.costDelta()).isEqualByComparingTo("0");
        assertThat(e.taxCostDelta()).isEqualByComparingTo("38.83694967");
        assertThat(e.fractionShares()).isEqualByComparingTo("0");
        assertThat(e.exDate()).isEqualTo(LocalDate.of(2025, 12, 18));

        // corretora (as telas): 2.078,74 / 600 = 3,46 — o que o Nubank mostra
        Position p = position(txs, events);
        assertThat(ledgerShares(txs, out)).isEqualByComparingTo(p.shares()).isEqualByComparingTo("600");
        assertThat(p.invested()).isEqualByComparingTo("2078.74");
        assertThat(p.averagePrice()).isEqualByComparingTo("3.46456667");

        // Receita (o IR): + 3 x 12,94564989 = 2.117,58 / 600 = 3,53
        Position tax = calculator.calculate(false, txs, PositionEvents.ofStockEvents(events), TODAY, CostBasis.TAX);
        assertThat(tax.shares()).isEqualByComparingTo("600");
        assertThat(tax.invested()).isEqualByComparingTo("2117.58");
        assertThat(tax.averagePrice()).isEqualByComparingTo("3.52929492");
    }

    @Test
    @DisplayName("Grupamento com fracao e desdobramento depois: variacao negativa, fracao e custo que saiu com ela")
    void reverseSplitWithFractionThenSplit() {
        List<Transaction> txs = List.of(
            tx(TransactionType.BUY, "2024-01-10", "105", "10.00", 1),
            tx(TransactionType.BUY, "2025-03-10", "5", "90.00", 2),
            tx(TransactionType.SELL, "2025-08-01", "6", "95.00", 3));
        List<StockEventStep> events = List.of(
            event(1, StockEventType.GRUPAMENTO, "2024-05-24", "0.1", null),
            event(2, StockEventType.DESDOBRAMENTO, "2025-09-15", "300", null));

        List<AppliedStockEvent> out = applied(txs, events);

        assertThat(out).hasSize(2);
        AppliedStockEvent reverse = out.get(0);
        assertThat(reverse.type()).isEqualTo(StockEventType.GRUPAMENTO);
        assertThat(reverse.sharesBefore()).isEqualByComparingTo("105");
        assertThat(reverse.sharesAfter()).isEqualByComparingTo("10");
        assertThat(reverse.sharesDelta()).isEqualByComparingTo("-95");
        assertThat(reverse.fractionShares()).isEqualByComparingTo("0.5");
        assertThat(reverse.costDelta()).isEqualByComparingTo("-50");
        assertThat(reverse.taxUnitCost()).isNull();

        AppliedStockEvent split = out.get(1);
        assertThat(split.sharesBefore()).isEqualByComparingTo("9");
        assertThat(split.sharesAfter()).isEqualByComparingTo("36");
        assertThat(split.costDelta()).isEqualByComparingTo("0");

        assertThat(ledgerShares(txs, out)).isEqualByComparingTo(position(txs, events).shares());
    }

    @Test
    @DisplayName("Evento antes da primeira compra ou depois da venda total nao entra no extrato")
    void eventsOutsideThePosition() {
        List<Transaction> txs = List.of(
            tx(TransactionType.BUY, "2024-11-28", "100", "20.00", 1),
            tx(TransactionType.SELL, "2025-06-02", "100", "25.00", 2));
        List<StockEventStep> events = List.of(
            event(1, StockEventType.BONIFICACAO, "1996-06-17", "20", null),
            event(2, StockEventType.DESDOBRAMENTO, "2025-10-01", "100", null));

        assertThat(applied(txs, events)).isEmpty();
    }

    @Test
    @DisplayName("Bonificacao sem custo do IR: custo do IR nulo; a que so gera fracao fica de fora")
    void bonusWithoutCostAndFractionOnly() {
        List<StockEventStep> events = List.of(event(7, StockEventType.BONIFICACAO, "2025-12-18", "2", null));

        List<AppliedStockEvent> withShares = applied(
            List.of(tx(TransactionType.BUY, "2025-01-10", "250", "10.00", 1)), events);
        assertThat(withShares).hasSize(1);
        assertThat(withShares.get(0).sharesDelta()).isEqualByComparingTo("5");
        assertThat(withShares.get(0).taxUnitCost()).isNull();
        assertThat(withShares.get(0).taxCostDelta()).isNull();
        assertThat(withShares.get(0).costDelta()).isEqualByComparingTo("0");

        assertThat(applied(List.of(tx(TransactionType.BUY, "2025-01-10", "20", "10.00", 1)), events)).isEmpty();
    }

    @Test
    @DisplayName("Compra na data-com tem direito; compra na data ex nao entra no 'antes' do evento")
    void buyOnDataComAndOnExDate() {
        List<Transaction> txs = List.of(
            tx(TransactionType.BUY, "2026-04-15", "10", "30.00", 1),
            tx(TransactionType.BUY, "2026-04-16", "10", "15.00", 2));
        List<AppliedStockEvent> out = applied(txs,
            List.of(event(1, StockEventType.DESDOBRAMENTO, "2026-04-15", "100", null)));

        assertThat(out).hasSize(1);
        assertThat(out.get(0).sharesBefore()).isEqualByComparingTo("10");
        assertThat(out.get(0).sharesAfter()).isEqualByComparingTo("20");
        assertThat(out.get(0).exDate()).isEqualTo(LocalDate.of(2026, 4, 16));
    }

    @Test
    @DisplayName("Evento com data-com no futuro ainda nao entra")
    void futureEvent() {
        assertThat(applied(List.of(tx(TransactionType.BUY, "2026-01-10", "10", "30.00", 1)),
            List.of(event(1, StockEventType.DESDOBRAMENTO, "2026-12-15", "100", null)))).isEmpty();
    }
}
