package com.primevest.domain.strategy;

import com.primevest.domain.portfolio.PortfolioAsset;
import com.primevest.domain.portfolio.Position;
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

    private record Linha(PortfolioAsset pa, Position position) {}

    private static Linha linha(String aplicado, String resgatado, String valorizacao, boolean liquidada, boolean reserva) {
        PortfolioAsset pa = PortfolioAsset.builder()
            .appreciation(new BigDecimal(valorizacao))
            .isSettled(liquidada)
            .isReservation(reserva)
            .build();
        pa.setId(UUID.randomUUID());
        return new Linha(pa, new Position(null, null, new BigDecimal(aplicado), new BigDecimal(resgatado)));
    }

    private static StrategyHoldings.Holdings carteira(Linha... linhas) {
        return new StrategyHoldings.Holdings(
            Arrays.stream(linhas).map(Linha::pa).toList(),
            Arrays.stream(linhas).collect(Collectors.toMap(l -> l.pa().getId(), Linha::position)));
    }

    @Test
    @DisplayName("Acao que valorizou vale custo + valorizacao")
    void valorizacaoPositivaEntra() {
        Linha acao = linha("1000.00", "0", "250.00", false, false);
        assertThat(carteira(acao).held(acao.pa())).isEqualByComparingTo("1250.00");
    }

    @Test
    @DisplayName("Acao que desvalorizou vale custo - perda")
    void valorizacaoNegativaEntra() {
        Linha acao = linha("1000.00", "0", "-300.00", false, false);
        assertThat(carteira(acao).held(acao.pa())).isEqualByComparingTo("700.00");
    }

    @Test
    @DisplayName("Renda fixa com resgate parcial vale o saldo na curva: aplicado + valorizacao - resgatado")
    void rendaFixaComResgate() {
        // Aplicou 10.000, resgatou 4.000; na curva o saldo e 6.500 -> valorizacao gravada = 6.500 + 4.000 - 10.000.
        Linha cdb = linha("10000.00", "4000.00", "500.00", false, true);
        assertThat(carteira(cdb).held(cdb.pa())).isEqualByComparingTo("6500.00");
    }

    @Test
    @DisplayName("Posicao liquidada nao vale nada, mesmo com o ganho do resgate gravado")
    void liquidadaValeZero() {
        Linha cdb = linha("10000.00", "10800.00", "800.00", true, false);
        assertThat(carteira(cdb).held(cdb.pa())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Nunca negativo")
    void nuncaNegativo() {
        Linha cripto = linha("100.00", "0", "-150.00", false, false);
        assertThat(carteira(cripto).held(cripto.pa())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Total e reserva somam o valor atual")
    void totalEReserva() {
        Linha acao = linha("1000.00", "0", "200.00", false, false);
        Linha cdb = linha("3000.00", "0", "150.00", false, true);
        StrategyHoldings.Holdings h = carteira(acao, cdb);
        assertThat(h.total()).isEqualByComparingTo("4350.00");
        assertThat(h.reservation()).isEqualByComparingTo("3150.00");
    }
}
