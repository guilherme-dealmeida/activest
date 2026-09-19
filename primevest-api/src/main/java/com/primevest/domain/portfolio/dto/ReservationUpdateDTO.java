package com.primevest.domain.portfolio.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Estado desejado da reserva de emergencia para uma posicao.
 *
 * <p>E o ESTADO, nao um "inverta o que estiver la": dois cliques rapidos no
 * switch nao se cancelam por ordem de chegada, e reenviar a mesma requisicao
 * nao muda o resultado.</p>
 */
public record ReservationUpdateDTO(
    @NotNull(message = "Informe se a posição compõe a reserva de emergência")
    Boolean isReservation
) {}
