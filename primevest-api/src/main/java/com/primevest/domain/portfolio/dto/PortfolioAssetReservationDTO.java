package com.primevest.domain.portfolio.dto;

import java.util.UUID;

/**
 * Resposta da marcacao de reserva de emergencia.
 *
 * <p>Devolve o estado gravado e o ticker, que e o que a tela precisa para
 * montar a mensagem. O texto em si fica no frontend: mensagem de UI em
 * resposta de API espalharia portugues pelo backend e amarraria o texto a um
 * deploy da API.</p>
 */
public record PortfolioAssetReservationDTO(
    UUID id,
    String ticker,
    boolean isReservation
) {}
