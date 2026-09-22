package com.primevest.domain.fixedincome;

import com.primevest.domain.asset.Asset;
import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.transaction.Transaction;
import com.primevest.domain.transaction.TransactionType;
import com.primevest.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regras E, F, G e J da renda fixa, e as duas formulas de venda.
 *
 * <p>O ultimo parametro de {@code recompute} e o que separa "a escrita foi numa
 * venda" de "a escrita foi numa compra": so o resgate autoriza corrigir a
 * valorizacao para cima.</p>
 */
class FixedIncomeSettlementTest {

    private final FixedIncomeSettlement settlement = new FixedIncomeSettlement();

    private PortfolioAsset posicao(String valorizacao) {
        Asset cdb = Asset.builder().ticker("CDB").name("Certificado de Depósito Bancário").build();
        return PortfolioAsset.builder()
            .asset(cdb)
            .appreciation(new BigDecimal(valorizacao))
            .build();
    }

    private static Transaction aplicacao(String data, String valor) {
        return porValor(TransactionType.BUY, data, valor, false);
    }

    private static Transaction resgate(String data, String valor) {
        return porValor(TransactionType.SELL, data, valor, false);
    }

    private static Transaction liquidacaoTotal(String data, String valor) {
        return porValor(TransactionType.SELL, data, valor, true);
    }

    private static Transaction porValor(TransactionType tipo, String data, String valor, boolean total) {
        return Transaction.builder()
            .type(tipo)
            .totalAmount(new BigDecimal(valor))
            .transactionDate(LocalDate.parse(data))
            .fullSettlement(total)
            .build();
    }

    @Test
    @DisplayName("Liquidacao total: a valorizacao passa a ser resgate menos aplicado e a posicao e liquidada")
    void liquidacaoTotalDefineAValorizacao() {
        PortfolioAsset pa = posicao("0.00");

        settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00"),
            liquidacaoTotal("2026-09-10", "1120.00")), true);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("120.00");
        assertThat(pa.isSettled()).isTrue();
    }

    @Test
    @DisplayName("Regra G — liquidacao total abaixo do aplicado e recusada: nao existe valorizacao negativa")
    void regraG_liquidacaoAbaixoDoAplicado() {
        PortfolioAsset pa = posicao("0.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00"),
            liquidacaoTotal("2026-09-10", "900.00")), true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Liquidação total de CDB")
            .hasMessageContaining("Em renda fixa não existe valorização negativa");
    }

    @Test
    @DisplayName("Resgate parcial que alcanca o saldo deixa o centavo que a regra F exige")
    void resgateParcialDeixaUmCentavo() {
        PortfolioAsset pa = posicao("0.00");

        settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00"),
            resgate("2026-09-10", "1050.00")), true);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("50.01");
        assertThat(pa.isSettled()).isFalse();
    }

    @Test
    @DisplayName("Resgate parcial dentro do saldo nao mexe na valorizacao")
    void resgateDentroDoSaldoNaoCorrige() {
        PortfolioAsset pa = posicao("200.00");

        settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00"),
            resgate("2026-09-10", "300.00")), true);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("200.00");
        assertThat(pa.isSettled()).isFalse();
    }

    @Test
    @DisplayName("Regra E — compra excluida que deixa o resgatado acima do saldo e recusada, sem inventar valorizacao")
    void regraE_escritaEmCompraNaoCorrigeParaCima() {
        PortfolioAsset pa = posicao("0.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00"),
            resgate("2026-09-10", "1042.00")), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("o total resgatado")
            .hasMessageContaining("passaria do valor aplicado");
    }

    @Test
    @DisplayName("Regra F — resgate que iguala o saldo sem liquidacao total e recusado")
    void regraF_igualarSemLiquidar() {
        PortfolioAsset pa = posicao("100.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00"),
            resgate("2026-09-10", "1100.00")), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("igualaria o valor aplicado")
            .hasMessageContaining("Zerar a posição só é possível marcando a venda como liquidação total");
    }

    @Test
    @DisplayName("Regra J — sem a venda que liquidou, a posicao reabre")
    void regraJ_posicaoReabre() {
        PortfolioAsset pa = posicao("120.00");
        pa.setSettled(true);

        settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00")), false);

        assertThat(pa.isSettled()).isFalse();
        assertThat(pa.getAppreciation()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("As formulas miram um alvo: relancar a mesma venda chega ao mesmo numero")
    void calculoEIdempotente() {
        PortfolioAsset primeira = posicao("0.00");
        List<Transaction> historico = List.of(aplicacao("2026-01-10", "1000.00"),
            resgate("2026-09-10", "1050.00"));

        settlement.recompute(primeira, historico, true);
        BigDecimal depoisDaPrimeira = primeira.getAppreciation();
        settlement.recompute(primeira, historico, true);

        assertThat(primeira.getAppreciation()).isEqualByComparingTo(depoisDaPrimeira);
    }

    @Test
    @DisplayName("Posicao so com aplicacoes fica aberta e sem valorizacao")
    void somenteAplicacoes() {
        PortfolioAsset pa = posicao("0.00");

        settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00"),
            aplicacao("2026-02-10", "500.00")), false);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("0.00");
        assertThat(pa.isSettled()).isFalse();
    }

    @Test
    @DisplayName("Regra G — valorizacao negativa ja gravada e recusada em qualquer passagem")
    void regraG_valorizacaoNegativaGravada() {
        PortfolioAsset pa = posicao("-10.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(aplicacao("2026-01-10", "1000.00")), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("ficaria negativa")
            .hasMessageContaining("Em renda fixa isso não existe");
    }
}
