package com.activest.domain.strategy.dto;

import com.activest.domain.strategy.ReservationType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A estrategia de reserva e como a carteira esta em relacao a ela.
 *
 * <p>Os numeros apurados vem junto porque a aba "Estrategia Reserva" mostra
 * % atual, % diff e o grafico na mesma tela em que se edita o alvo.</p>
 *
 * @param targetPercentage    alvo do tipo PERCENTAGE; nulo no VALUE
 * @param targetValue         alvo do tipo VALUE; nulo no PERCENTAGE
 * @param totalPortfolioValue a carteira inteira: reserva + geracao de renda
 * @param reservationValue    soma das posicoes marcadas como reserva
 * @param incomeValue         o restante: geracao de renda
 * @param currentPercentage   reserva / carteira, em %
 * @param idealPercentage     no PERCENTAGE, o alvo; no VALUE, alvo / carteira
 *                            (limitado a 100%) — e o que o % diff compara
 * @param diffPercentage      atual - ideal: negativo e abaixo do alvo
 */
public record StrategyReservationDTO(
    UUID id,
    ReservationType typeReservation,
    BigDecimal targetPercentage,
    BigDecimal targetValue,
    BigDecimal totalPortfolioValue,
    BigDecimal reservationValue,
    BigDecimal incomeValue,
    BigDecimal currentPercentage,
    BigDecimal idealPercentage,
    BigDecimal diffPercentage
) {}
