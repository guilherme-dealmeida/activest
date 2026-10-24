package com.activest.domain.portfolio.dto;

import java.math.BigDecimal;

/**
 * A carteira dividida entre reserva de emergencia e geracao de renda, como
 * esta hoje.
 *
 * <p>Mesma base de valor do grafico por tipo ({@code /portfolio/allocation}):
 * valor atual, ou seja, custo + valorizacao - resgates, so de posicoes em
 * aberto. Os dois graficos ficam lado a lado na aba "Carteira de
 * Investimentos"; o por tipo exclui a reserva, entao {@code incomeValue}
 * daqui e o total no centro dele.</p>
 *
 * <p>Nao e a base da estrategia de reserva (custo menos resgates, sem
 * valorizacao), que compara aportes; aqui a pergunta e "como a carteira esta
 * dividida agora".</p>
 *
 * <p>Sem cor: reserva e geracao de renda nao sao tipos de ativo, e as cores
 * do grafico sao da tela.</p>
 */
public record ReservationAllocationDTO(
    BigDecimal totalValue,
    BigDecimal reservationValue,
    BigDecimal reservationPercentage,
    BigDecimal incomeValue,
    BigDecimal incomePercentage
) {}
