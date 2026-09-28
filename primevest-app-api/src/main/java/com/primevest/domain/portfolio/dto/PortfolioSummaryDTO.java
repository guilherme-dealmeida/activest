package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioSummaryDTO(
    UUID id,
    String name,
    BigDecimal totalInvested,
    BigDecimal currentValue,
    BigDecimal totalDividends,
    BigDecimal totalReturn,
    BigDecimal returnPercentage
) {}
