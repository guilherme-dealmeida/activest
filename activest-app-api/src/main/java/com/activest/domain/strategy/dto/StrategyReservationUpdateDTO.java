package com.activest.domain.strategy.dto;

import com.activest.domain.strategy.ReservationType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Atualizacao da estrategia de reserva.
 *
 * <p>So o alvo do tipo escolhido e lido; o outro e descartado pelo service.
 * Mandar os dois nao e erro — a tela pode ter os dois preenchidos enquanto o
 * usuario troca de tipo —, mas faltar o do tipo escolhido e.</p>
 */
public record StrategyReservationUpdateDTO(
    @NotNull(message = "Informe o tipo de estratégia")
    ReservationType typeReservation,

    @DecimalMin(value = "0", message = "O percentual não pode ser negativo")
    @DecimalMax(value = "100", message = "O percentual não pode passar de 100%")
    BigDecimal targetPercentage,

    @DecimalMin(value = "0", message = "O valor não pode ser negativo")
    BigDecimal targetValue
) {}
