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

    private PortfolioAsset position(String appreciationValue) {
        Asset cdb = Asset.builder().ticker("CDB").name("Certificado de Depósito Bancário").build();
        return PortfolioAsset.builder()
            .asset(cdb)
            .appreciation(new BigDecimal(appreciationValue))
            .build();
    }

    private static Transaction investment(String data, String value) {
        return byValue(TransactionType.BUY, data, value, false);
    }

    private static Transaction redemption(String data, String value) {
        return byValue(TransactionType.SELL, data, value, false);
    }

    private static Transaction isFullSettlement_(String data, String value) {
        return byValue(TransactionType.SELL, data, value, true);
    }

    private static Transaction byValue(TransactionType typeValue, String data, String value, boolean total) {
        return Transaction.builder()
            .type(typeValue)
            .totalAmount(new BigDecimal(value))
            .transactionDate(LocalDate.parse(data))
            .fullSettlement(total)
            .build();
    }

    @Test
    @DisplayName("Liquidacao total: a valorizacao passa a ser resgate menos aplicado e a posicao e liquidada")
    void fullSettlementDefinesAppreciation() {
        PortfolioAsset pa = position("0.00");

        settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00"),
            isFullSettlement_("2026-09-10", "1120.00")), true);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("120.00");
        assertThat(pa.isSettled()).isTrue();
    }

    @Test
    @DisplayName("Regra G — liquidacao total abaixo do aplicado e recusada: nao existe valorizacao negativa")
    void ruleG_settlementBelowInvested() {
        PortfolioAsset pa = position("0.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00"),
            isFullSettlement_("2026-09-10", "900.00")), true))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Liquidação total de CDB")
            .hasMessageContaining("Em renda fixa não existe valorização negativa");
    }

    @Test
    @DisplayName("Resgate parcial que alcanca o saldo deixa o centavo que a regra F exige")
    void partialRedemptionLeavesOneCent() {
        PortfolioAsset pa = position("0.00");

        settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00"),
            redemption("2026-09-10", "1050.00")), true);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("50.01");
        assertThat(pa.isSettled()).isFalse();
    }

    @Test
    @DisplayName("Resgate parcial dentro do saldo nao mexe na valorizacao")
    void redemptionWithinBalanceDoesNotCorrect() {
        PortfolioAsset pa = position("200.00");

        settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00"),
            redemption("2026-09-10", "300.00")), true);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("200.00");
        assertThat(pa.isSettled()).isFalse();
    }

    @Test
    @DisplayName("Regra E — compra excluida que deixa o resgatado acima do saldo e recusada, sem inventar valorizacao")
    void ruleE_writeDownOnBuyDoesNotCorrectUp() {
        PortfolioAsset pa = position("0.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00"),
            redemption("2026-09-10", "1042.00")), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("o total resgatado")
            .hasMessageContaining("passaria do valor aplicado");
    }

    @Test
    @DisplayName("Regra F — resgate que iguala o saldo sem liquidacao total e recusado")
    void ruleF_matchWithoutSettling() {
        PortfolioAsset pa = position("100.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00"),
            redemption("2026-09-10", "1100.00")), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("igualaria o valor aplicado")
            .hasMessageContaining("Zerar a posição só é possível marcando a venda como liquidação total");
    }

    @Test
    @DisplayName("Regra J — sem a venda que liquidou, a posicao reabre")
    void ruleJ_positionReopens() {
        PortfolioAsset pa = position("120.00");
        pa.setSettled(true);

        settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00")), false);

        assertThat(pa.isSettled()).isFalse();
        assertThat(pa.getAppreciation()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("As formulas miram um alvo: relancar a mesma venda chega ao mesmo numero")
    void calculationIsIdempotent() {
        PortfolioAsset first = position("0.00");
        List<Transaction> history = List.of(investment("2026-01-10", "1000.00"),
            redemption("2026-09-10", "1050.00"));

        settlement.recompute(first, history, true);
        BigDecimal afterTheFirst = first.getAppreciation();
        settlement.recompute(first, history, true);

        assertThat(first.getAppreciation()).isEqualByComparingTo(afterTheFirst);
    }

    @Test
    @DisplayName("Posicao so com aplicacoes fica aberta e sem valorizacao")
    void onlyInvestments() {
        PortfolioAsset pa = position("0.00");

        settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00"),
            investment("2026-02-10", "500.00")), false);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("0.00");
        assertThat(pa.isSettled()).isFalse();
    }

    @Test
    @DisplayName("Posicao sem nenhuma transacao zera a valorizacao que sobrou de uma venda excluida")
    void withoutTransactionZeroesAppreciation() {
        PortfolioAsset pa = position("300.00");

        settlement.recompute(pa, List.of(), false);

        assertThat(pa.getAppreciation()).isEqualByComparingTo("0.00");
        assertThat(pa.isSettled()).isFalse();
    }

    @Test
    @DisplayName("Regra G — valorizacao negativa ja gravada e recusada em qualquer passagem")
    void ruleG_negativeAppreciationStored() {
        PortfolioAsset pa = position("-10.00");

        assertThatThrownBy(() -> settlement.recompute(pa, List.of(investment("2026-01-10", "1000.00")), false))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("ficaria negativa")
            .hasMessageContaining("Em renda fixa isso não existe");
    }
}
