package com.primevest.domain.portfolio;

import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras A, B e I — as que valem para qualquer tipo de ativo.
 */
class PositionGuardTest {

    private final PositionGuard guard = new PositionGuard();

    private static Transaction tx(TransactionType tipo, String data, String cotas) {
        return Transaction.builder()
            .type(tipo)
            .shares(new BigDecimal(cotas))
            .pricePerShare(new BigDecimal("30.00"))
            .transactionDate(LocalDate.parse(data))
            .build();
    }

    @Test
    @DisplayName("Regra A — venda sem posicao com compras e recusada")
    void regraA_vendaNaoAbrePosicao() {
        assertThatThrownBy(() -> guard.validateSellHasPosition(false, "PETR4"))
            .isInstanceOf(BusinessException.class)
            .hasMessage("Não há posição de PETR4 na carteira para vender.");
    }

    @Test
    @DisplayName("Regra A — com compras na posicao, a venda passa")
    void regraA_comCompraPassa() {
        assertThatCode(() -> guard.validateSellHasPosition(true, "PETR4")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Regra B — saldo suficiente em toda a linha do tempo passa")
    void regraB_saldoSuficiente() {
        assertThatCode(() -> guard.validateShareLedger(List.of(
            tx(TransactionType.BUY, "2026-09-01", "10"),
            tx(TransactionType.SELL, "2026-09-04", "8")), "PETR4"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Regra B — a falta e apontada na data da venda, com quantas cotas faltam")
    void regraB_mensagemTrazDataEQuantidade() {
        assertThatThrownBy(() -> guard.validateShareLedger(List.of(
            tx(TransactionType.BUY, "2026-09-01", "10"),
            tx(TransactionType.SELL, "2026-09-04", "8"),
            tx(TransactionType.SELL, "2026-09-11", "4")), "PETR4"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Saldo insuficiente de PETR4 em 11/09/2026")
            .hasMessageContaining("faltam 2 cotas nessa data")
            .hasMessageContaining("Ajuste as compras do período antes de lançar esta venda");
    }

    @Test
    @DisplayName("Regra B — olha a linha do tempo inteira: a compra posterior nao cobre a venda anterior")
    void regraB_compraPosteriorNaoSalva() {
        assertThatThrownBy(() -> guard.validateShareLedger(List.of(
            tx(TransactionType.BUY, "2026-09-01", "3"),
            tx(TransactionType.SELL, "2026-09-14", "5"),
            tx(TransactionType.BUY, "2026-09-15", "4")), "Tesouro IPCA+ 2050"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("em 14/09/2026")
            .hasMessageContaining("faltam 2 cotas");
    }

    @Test
    @DisplayName("Regra B — transacao sem quantidade (renda fixa) nao mexe no saldo por cota")
    void regraB_ignoraTransacaoSemQuantidade() {
        Transaction semCotas = Transaction.builder()
            .type(TransactionType.BUY)
            .totalAmount(new BigDecimal("1000.00"))
            .transactionDate(LocalDate.parse("2026-09-01"))
            .build();

        assertThatCode(() -> guard.validateShareLedger(List.of(semCotas), "CDB")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Regra B — a quantidade sai sem zeros a direita na mensagem")
    void regraB_numeroLegivel() {
        assertThatThrownBy(() -> guard.validateShareLedger(List.of(
            tx(TransactionType.BUY, "2026-09-01", "1.50000000"),
            tx(TransactionType.SELL, "2026-09-02", "2.00000000")), "BTC"))
            .hasMessageContaining("faltam 0.5 cotas");
    }

    @Test
    @DisplayName("Regra I — posicao em aberto sem valor aplicado e recusada")
    void regraI_posicaoAbertaSemAplicado() {
        Position zerada = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatThrownBy(() -> guard.validateApplied(zerada, false, true, "CDB (BTG)"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("A posição de CDB (BTG) ficaria sem valor aplicado")
            .hasMessageContaining("Uma posição em aberto precisa de pelo menos uma compra");
    }

    @Test
    @DisplayName("Regra I — posicao liquidada pode terminar zerada")
    void regraI_liquidadaPassa() {
        Position zerada = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatCode(() -> guard.validateApplied(zerada, true, true, "CDB")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Regra I — posicao sem transacao nenhuma passa (acabou de nascer)")
    void regraI_semTransacaoPassa() {
        Position zerada = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        assertThatCode(() -> guard.validateApplied(zerada, false, false, "CDB")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Regra I — posicao com aplicado passa")
    void regraI_comAplicadoPassa() {
        Position comValor = new Position(new BigDecimal("10"), new BigDecimal("30.0000"),
            new BigDecimal("300.00"), BigDecimal.ZERO);

        assertThatCode(() -> guard.validateApplied(comValor, false, true, "PETR4")).doesNotThrowAnyException();
        assertThat(comValor.hasInvestment()).isTrue();
    }
}
