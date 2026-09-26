package com.primevest.domain.portfolio;

import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A regra da posicao, sem banco: so transacoes entrando e Position saindo.
 */
class PositionCalculatorTest {

    private final PositionCalculator calculator = new PositionCalculator(null, null, null);

    private static Transaction tx(TransactionType tipo, String data, String cotas, String preco, int ordem) {
        BigDecimal qtd = cotas == null ? null : new BigDecimal(cotas);
        BigDecimal unit = preco == null ? null : new BigDecimal(preco);
        return Transaction.builder()
            .type(tipo)
            .shares(qtd)
            .pricePerShare(unit)
            .totalAmount(qtd != null && unit != null ? qtd.multiply(unit) : null)
            .transactionDate(LocalDate.parse(data))
            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(ordem))
            .build();
    }

    private static Transaction porValor(TransactionType tipo, String data, String total, int ordem) {
        return Transaction.builder()
            .type(tipo)
            .totalAmount(new BigDecimal(total))
            .transactionDate(LocalDate.parse(data))
            .createdAt(LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(ordem))
            .build();
    }

    @Nested
    @DisplayName("Saldo por cota")
    class PorCota {

        @Test
        @DisplayName("Compra soma cotas e custo")
        void compraSomaCotasECusto() {
            Position p = calculator.calculate(false, List.of(tx(TransactionType.BUY, "2026-09-01", "10", "30.00", 1)));

            assertThat(p.shares()).isEqualByComparingTo("10");
            assertThat(p.invested()).isEqualByComparingTo("300.00");
            assertThat(p.averagePrice()).isEqualByComparingTo("30.0000");
        }

        @Test
        @DisplayName("A venda tira custo pelo preco medio do momento, e o preco medio nao muda")
        void exemploDoProjeto_precoMedio15Custo1500() {
            Position p = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "100", "10.00", 1),
                tx(TransactionType.BUY, "2026-09-02", "100", "20.00", 2),
                tx(TransactionType.SELL, "2026-09-03", "100", "30.00", 3)));

            assertThat(p.shares()).isEqualByComparingTo("100");
            assertThat(p.invested()).isEqualByComparingTo("1500.00");
            assertThat(p.averagePrice()).isEqualByComparingTo("15.0000");
        }

        @Test
        @DisplayName("O preco de venda nao entra no custo: vender caro ou barato da o mesmo resultado")
        void precoDeVendaNaoAfetaOCusto() {
            Position cara = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "100", "10.00", 1),
                tx(TransactionType.SELL, "2026-09-03", "50", "90.00", 2)));
            Position barata = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "100", "10.00", 1),
                tx(TransactionType.SELL, "2026-09-03", "50", "1.00", 2)));

            assertThat(cara.invested()).isEqualByComparingTo(barata.invested());
            assertThat(cara.invested()).isEqualByComparingTo("500.00");
        }

        @Test
        @DisplayName("Venda de tudo zera cotas e custo")
        void vendaTotalZera() {
            Position p = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "10", "30.00", 1),
                tx(TransactionType.SELL, "2026-09-02", "10", "31.00", 2)));

            assertThat(p.shares()).isEqualByComparingTo("0");
            assertThat(p.invested()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Venda maior que a posicao zera em vez de deixar saldo negativo")
        void vendaMaiorQueAPosicaoZera() {
            Position p = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "10", "30.00", 1),
                tx(TransactionType.SELL, "2026-09-02", "15", "31.00", 2)));

            assertThat(p.shares()).isEqualByComparingTo("0");
            assertThat(p.invested()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Venda sem cotas em carteira e ignorada")
        void vendaSemPosicaoEIgnorada() {
            Position p = calculator.calculate(false, List.of(
                tx(TransactionType.SELL, "2026-09-01", "5", "30.00", 1),
                tx(TransactionType.BUY, "2026-09-02", "10", "30.00", 2)));

            assertThat(p.shares()).isEqualByComparingTo("10");
            assertThat(p.invested()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("Preco medio com 8 casas (a escala de price_per_share) e custo com 2")
        void escalas() {
            Position p = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "3", "10.00", 1),
                tx(TransactionType.BUY, "2026-09-02", "3", "10.01", 2)));

            assertThat(p.averagePrice().scale()).isEqualTo(8);
            assertThat(p.invested().scale()).isEqualTo(2);
            assertThat(p.averagePrice()).isEqualByComparingTo("10.0050");
        }

        @Test
        @DisplayName("Cripto de fracao de centavo: o preco medio nao arredonda para R$ 0,0001")
        void precoMedioDeFracaoDeCentavo() {
            // Com 4 casas o medio abaixo virava 0.0001 — 40% acima do real.
            Position p = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "1000000", "0.00007000", 1),
                tx(TransactionType.BUY, "2026-09-02", "1000000", "0.00007246", 2)));

            assertThat(p.averagePrice()).isEqualByComparingTo("0.00007123");
        }

        @Test
        @DisplayName("Cripto: as 8 casas da quantidade sobrevivem ao calculo")
        void fracaoDeCripto() {
            Position p = calculator.calculate(false, List.of(
                tx(TransactionType.BUY, "2026-09-01", "0.00012345", "500000.00", 1)));

            assertThat(p.shares()).isEqualByComparingTo("0.00012345");
            assertThat(p.invested()).isEqualByComparingTo("61.73");
        }
    }

    @Nested
    @DisplayName("Ordem cronologica")
    class Cronologia {

        @Test
        @DisplayName("Ordena por data e, no mesmo dia, pela criacao")
        void dataDepoisCriacao() {
            Transaction compraDoDia = tx(TransactionType.BUY, "2026-09-04", "10", "30.00", 2);
            Transaction vendaDoDia = tx(TransactionType.SELL, "2026-09-04", "10", "31.00", 3);
            Transaction compraAntiga = tx(TransactionType.BUY, "2026-09-01", "5", "20.00", 1);

            List<Transaction> ordenadas =
                PositionCalculator.chronological(List.of(vendaDoDia, compraAntiga, compraDoDia));

            assertThat(ordenadas).containsExactly(compraAntiga, compraDoDia, vendaDoDia);
        }

        @Test
        @DisplayName("No mesmo dia, a venda lancada DEPOIS da compra enxerga as cotas dela")
        void vendaDepoisDaCompraNoMesmoDia() {
            Position p = calculator.calculate(false, PositionCalculator.chronological(List.of(
                tx(TransactionType.SELL, "2026-09-04", "12", "31.00", 3),
                tx(TransactionType.BUY, "2026-09-04", "10", "30.00", 2),
                tx(TransactionType.BUY, "2026-09-01", "5", "20.00", 1))));

            assertThat(p.shares()).isEqualByComparingTo("3");
        }
    }

    @Nested
    @DisplayName("sharesOn — cotas numa data (a data-com dos proventos)")
    class CotasNaData {

        private final List<Transaction> historico = List.of(
            tx(TransactionType.BUY, "2026-09-01", "10", "30.00", 1),
            tx(TransactionType.SELL, "2026-09-10", "4", "31.00", 2),
            tx(TransactionType.BUY, "2026-09-20", "2", "32.00", 3));

        @Test
        @DisplayName("Compra na propria data conta")
        void naDataDaCompra() {
            assertThat(calculator.sharesOn(historico, LocalDate.parse("2026-09-01"))).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("Antes da primeira compra, zero")
        void antesDeTudo() {
            assertThat(calculator.sharesOn(historico, LocalDate.parse("2026-08-31"))).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Depois da venda, o saldo da data")
        void depoisDaVenda() {
            assertThat(calculator.sharesOn(historico, LocalDate.parse("2026-09-15"))).isEqualByComparingTo("6");
            assertThat(calculator.sharesOn(historico, LocalDate.parse("2026-09-30"))).isEqualByComparingTo("8");
        }
    }

    @Nested
    @DisplayName("Renda fixa (lancada por valor total)")
    class PorValor {

        @Test
        @DisplayName("Aplicado e resgatado ficam separados; cotas e preco medio sao nulos")
        void aplicadoEResgatadoSeparados() {
            Position p = calculator.calculate(true, List.of(
                porValor(TransactionType.BUY, "2026-09-01", "1000.00", 1),
                porValor(TransactionType.BUY, "2026-09-05", "500.00", 2),
                porValor(TransactionType.SELL, "2026-09-20", "300.00", 3)));

            assertThat(p.totalShares()).isNull();
            assertThat(p.averagePrice()).isNull();
            assertThat(p.invested()).isEqualByComparingTo("1500.00");
            assertThat(p.sold()).isEqualByComparingTo("300.00");
        }

        @Test
        @DisplayName("Sem transacao, a posicao e vazia")
        void semTransacao() {
            Position p = calculator.calculate(true, List.of());

            assertThat(p.invested()).isEqualByComparingTo("0.00");
            assertThat(p.sold()).isEqualByComparingTo("0.00");
            assertThat(p.hasInvestment()).isFalse();
        }
    }
}
