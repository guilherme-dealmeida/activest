package com.primevest.domain.portfolio;

import com.primevest.domain.corporateaction.StockEventStep;
import com.primevest.domain.corporateaction.StockEventType;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eventos do papel no calculo da posicao (PLANO_BRAPI.md, F5 e F6): o evento e
 * um passo a mais na linha do tempo, logo depois das transacoes da data-com, e
 * nenhuma transacao e reescrita.
 */
class PositionCalculatorEventosTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 9, 24);

    private final PositionCalculator calculator = new PositionCalculator(null, null, null);

    private static Transaction tx(TransactionType tipo, String data, String cotas, String preco, int ordem) {
        BigDecimal qtd = new BigDecimal(cotas);
        BigDecimal unit = new BigDecimal(preco);
        return Transaction.builder()
            .type(tipo)
            .shares(qtd)
            .pricePerShare(unit)
            .totalAmount(qtd.multiply(unit))
            .transactionDate(LocalDate.parse(data))
            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(ordem))
            .build();
    }

    private static StockEventStep evento(StockEventType tipo, String dataCom, String fator) {
        return new StockEventStep(LocalDate.parse(dataCom), tipo, new BigDecimal(fator), null);
    }

    private Position calcular(List<Transaction> txs, List<StockEventStep> eventos, List<CapitalReturn> capital) {
        return calculator.calculate(false, txs, new PositionEvents(eventos, capital), HOJE);
    }

    @Test
    @DisplayName("Compra, desdobramento 1:2 e venda de cotas novas: a venda cabe no saldo e o preco medio e o ajustado")
    void desdobramentoUmParaDois_dobraCotasEMantemCusto() {
        Position p = calcular(List.of(
                tx(TransactionType.BUY, "2026-03-02", "100", "30.00", 1),
                tx(TransactionType.SELL, "2026-05-04", "50", "16.00", 2)),
            List.of(evento(StockEventType.DESDOBRAMENTO, "2026-04-15", "100")), List.of());

        assertThat(p.shares()).isEqualByComparingTo("150");
        assertThat(p.averagePrice()).isEqualByComparingTo("15.0000");
        assertThat(p.invested()).isEqualByComparingTo("2250.00");
    }

    @Test
    @DisplayName("Compra NA data-com tem direito ao evento; compra na data ex ja vem na quantidade nova")
    void dataComTemDireitoDataExNao() {
        StockEventStep split = evento(StockEventType.DESDOBRAMENTO, "2026-04-15", "100");

        Position naDataCom = calcular(List.of(tx(TransactionType.BUY, "2026-04-15", "10", "30.00", 1)),
            List.of(split), List.of());
        Position naDataEx = calcular(List.of(tx(TransactionType.BUY, "2026-04-16", "10", "15.00", 1)),
            List.of(split), List.of());

        assertThat(naDataCom.shares()).isEqualByComparingTo("20");
        assertThat(naDataEx.shares()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Dois eventos seguidos: grupamento 10:1 e depois bonificacao de 5% sem custo")
    void doisEventosSeguidos() {
        Position p = calcular(List.of(tx(TransactionType.BUY, "2023-01-10", "1000", "5.00", 1)),
            List.of(evento(StockEventType.GRUPAMENTO, "2024-05-24", "0.1"),
                evento(StockEventType.BONIFICACAO, "2025-12-29", "5")), List.of());

        assertThat(p.shares()).isEqualByComparingTo("105");
        assertThat(p.invested()).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("Evento antes da primeira compra nao afeta nada")
    void eventoAntesDaPrimeiraCompra() {
        Position p = calcular(List.of(tx(TransactionType.BUY, "2026-06-01", "10", "20.00", 1)),
            List.of(evento(StockEventType.DESDOBRAMENTO, "2008-04-25", "100")), List.of());

        assertThat(p.shares()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Evento depois de zerar a posicao nao afeta nada")
    void eventoDepoisDeZerar() {
        Position p = calcular(List.of(
                tx(TransactionType.BUY, "2026-01-05", "10", "20.00", 1),
                tx(TransactionType.SELL, "2026-02-05", "10", "22.00", 2)),
            List.of(evento(StockEventType.DESDOBRAMENTO, "2026-03-01", "100")), List.of());

        assertThat(p.shares()).isEqualByComparingTo("0");
        assertThat(p.invested()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Evento com data-com no futuro ainda nao vale")
    void eventoFuturo() {
        Position p = calcular(List.of(tx(TransactionType.BUY, "2026-09-01", "10", "20.00", 1)),
            List.of(evento(StockEventType.DESDOBRAMENTO, "2026-10-15", "100")), List.of());

        assertThat(p.shares()).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("Amortizacao (B13): sai do custo na data de pagamento, cotas nao mudam, preco medio cai")
    void amortizacaoReduzCusto() {
        Position p = calcular(List.of(tx(TransactionType.BUY, "2026-01-05", "100", "10.00", 1)),
            List.of(), List.of(new CapitalReturn(LocalDate.parse("2026-06-15"), new BigDecimal("150.00"))));

        assertThat(p.shares()).isEqualByComparingTo("100");
        assertThat(p.invested()).isEqualByComparingTo("850.00");
        assertThat(p.averagePrice()).isEqualByComparingTo("8.5000");
    }

    @Test
    @DisplayName("Amortizacao maior que o custo zera o custo; nunca fica negativo")
    void amortizacaoNaoNegativa() {
        Position p = calcular(List.of(tx(TransactionType.BUY, "2026-01-05", "10", "10.00", 1)),
            List.of(), List.of(new CapitalReturn(LocalDate.parse("2026-06-15"), new BigDecimal("150.00"))));

        assertThat(p.invested()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Amortizacao com pagamento no futuro ainda nao reduz o custo")
    void amortizacaoFutura() {
        Position p = calcular(List.of(tx(TransactionType.BUY, "2026-01-05", "100", "10.00", 1)),
            List.of(), List.of(new CapitalReturn(LocalDate.parse("2026-12-15"), new BigDecimal("150.00"))));

        assertThat(p.invested()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("sharesOn com evento: a data-com do proprio evento ainda e a quantidade antiga")
    void sharesOnComEvento() {
        List<Transaction> txs = List.of(tx(TransactionType.BUY, "2026-03-02", "100", "30.00", 1));
        List<StockEventStep> eventos = List.of(evento(StockEventType.DESDOBRAMENTO, "2026-04-15", "100"));

        assertThat(calculator.sharesOn(txs, eventos, LocalDate.parse("2026-04-15"))).isEqualByComparingTo("100");
        assertThat(calculator.sharesOn(txs, eventos, LocalDate.parse("2026-04-16"))).isEqualByComparingTo("200");
    }

    @Test
    @DisplayName("Sem eventos, o calculo e o de sempre")
    void semEventos() {
        List<Transaction> txs = List.of(tx(TransactionType.BUY, "2026-03-02", "10", "30.00", 1));

        assertThat(calcular(txs, List.of(), List.of()))
            .isEqualTo(calculator.calculate(false, txs));
    }
}
