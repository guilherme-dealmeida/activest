package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioSummaryDTO(
    UUID id,
    String name,
    BigDecimal totalInvested,
    /** Valor atual BRUTO (cotacao, PU do Tesouro, curva da renda fixa). */
    BigDecimal currentValue,
    BigDecimal totalDividends,
    BigDecimal totalReturn,
    BigDecimal returnPercentage,
    /**
     * Aportes liquidos dos ultimos 12 meses: compras - o que as vendas devolveram (liquido na renda fixa e no
     * Tesouro) — card Aportes do Dashboard (17/10/2026). Negativo quando se resgatou mais do que se aplicou.
     */
    BigDecimal contributions12m,
    /** Media mensal dos aportes dos ultimos 12 meses (contributions12m / 12). */
    BigDecimal contributionsMonthlyAverage12m,
    /**
     * Valor atual LIQUIDO estimado: na renda fixa e no Tesouro, o liquido de resgate na data (bruto - IOF - IR,
     * PLANO_VENDA_RENDA_FIXA R11); nos demais tipos (sem calculo de imposto no PrimeVest), o proprio valor atual.
     */
    BigDecimal currentNetValue
) {}
