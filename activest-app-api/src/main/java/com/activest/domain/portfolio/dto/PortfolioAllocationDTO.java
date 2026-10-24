package com.activest.domain.portfolio.dto;

import java.math.BigDecimal;

public record PortfolioAllocationDTO(
    String typeName,
    String colorHex,
    BigDecimal value,
    BigDecimal percentage
) {}
