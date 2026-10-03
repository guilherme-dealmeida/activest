package com.primevest.domain.strategy.dto;

import com.primevest.domain.strategy.ReservationType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sugestao de aporte.
 *
 * <p>{@code totalPortfolioValue} e {@code items} sao a geracao de renda, por
 * tipo: posicao de reserva nunca entra nessa conta. A reserva tem a sua linha
 * propria em {@code reservation}, e as duas nao se sobrepoem.</p>
 */
public record RebalanceDTO(
    BigDecimal totalPortfolioValue,
    List<RebalanceItem> items,
    ReservationRebalance reservation
) {
    public record RebalanceItem(
        String typeName,
        BigDecimal idealPercentage,
        BigDecimal currentPercentage,
        BigDecimal diffPercentage,
        BigDecimal currentValue,
        BigDecimal idealValue,
        BigDecimal amountToRebalance
    ) {}

    /**
     * Linha da tabela "Rebalanceamento Reserva de Emergencia".
     *
     * @param idealPercentage   alvo em % no PERCENTAGE; nulo no VALUE (a tela mostra "—")
     * @param targetValue       alvo em R$ no VALUE; nulo no PERCENTAGE
     * @param currentValue      aportado: o que a reserva vale hoje
     * @param amountToRebalance aportar; negativo quando a reserva esta acima do alvo
     * @param idealValue        estrategico: a reserva depois do aporte sugerido
     * @param currentPercentage % da carteira que a reserva ocupa hoje
     * @param diffPercentage    atual - ideal (no VALUE, ideal = alvo / carteira)
     */
    public record ReservationRebalance(
        ReservationType typeReservation,
        BigDecimal idealPercentage,
        BigDecimal targetValue,
        BigDecimal currentValue,
        BigDecimal amountToRebalance,
        BigDecimal idealValue,
        BigDecimal currentPercentage,
        BigDecimal diffPercentage
    ) {}
}
