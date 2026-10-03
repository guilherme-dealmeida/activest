package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioAllocationDTO(
    String typeName,
    String colorHex,
    BigDecimal value,
    BigDecimal percentage
) {}
