package com.activest.domain.strategy;

import com.activest.domain.portfolio.PortfolioAsset;
import com.activest.domain.portfolio.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valor de cada posicao para a estrategia: o valor atual (aplicado + valorizacao - resgatado), o mesmo da tela
 * Alocacao. Corrigido em 12/10/2026 — antes a valorizacao ficava de fora e o rebalanceamento comparava custo.
 */
class StrategyHoldingsTest {

    private record Row(PortfolioAsset pa, Position position) {}

    private static Row row(String invested, String redeemed, String appreciationValue, boolean settled, boolean reserve) {
        PortfolioAsset pa = PortfolioAsset.builder()
            .appreciation(new BigDecimal(appreciationValue))
            .isSettled(settled)
            .isReservation(reserve)
            .build();
        pa.setId(UUID.randomUUID());
        return new Row(pa, new Position(null, null, new BigDecimal(invested), new BigDecimal(redeemed)));
    }

    private static StrategyHoldings.Holdings portfolioEntity(Row... rows) {
        return new StrategyHoldings.Holdings(
            Arrays.stream(rows).map(Row::pa).toList(),
            Arrays.stream(rows).collect(Collectors.toMap(l -> l.pa().getId(), Row::position)));
    }

    @Test
    @DisplayName("Acao que valorizou vale custo + valorizacao")
    void positiveAppreciationEnters() {
        Row stock = row("1000.00", "0", "250.00", false, false);
        assertThat(portfolioEntity(stock).held(stock.pa())).isEqualByComparingTo("1250.00");
    }

    @Test
    @DisplayName("Acao que desvalorizou vale custo - perda")
    void negativeAppreciationEnters() {
        Row stock = row("1000.00", "0", "-300.00", false, false);
        assertThat(portfolioEntity(stock).held(stock.pa())).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("Renda fixa com resgate parcial vale o saldo na curva: aplicado + valorizacao - resgatado")
    void fixedIncomeWithRedemption() {
        // Aplicou 10.000, resgatou 4.000; na curva o saldo e 6.500 -> valorizacao gravada = 6.500 + 4.000 - 10.000.
        Row cdb = row("10000.00", "4000.00", "500.00", false, true);
        assertThat(portfolioEntity(cdb).held(cdb.pa())).isEqualByComparingTo("6500.00");
    }

    @Test
    @DisplayName("Posicao liquidada nao vale nada, mesmo com o ganho do resgate gravado")
    void settledIsWorthZero() {
        Row cdb = row("10000.00", "10800.00", "800.00", true, false);
        assertThat(portfolioEntity(cdb).held(cdb.pa())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Nunca negativo")
    void neverNegative() {
        Row crypto = row("100.00", "0", "-150.00", false, false);
        assertThat(portfolioEntity(crypto).held(crypto.pa())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Total e reserva somam o valor atual")
    void totalAndReserve() {
        Row stock = row("1000.00", "0", "200.00", false, false);
        Row cdb = row("3000.00", "0", "150.00", false, true);
        StrategyHoldings.Holdings h = portfolioEntity(stock, cdb);
        assertThat(h.total()).isEqualByComparingTo("4350.00");
        assertThat(h.reservation()).isEqualByComparingTo("3150.00");
    }
}
