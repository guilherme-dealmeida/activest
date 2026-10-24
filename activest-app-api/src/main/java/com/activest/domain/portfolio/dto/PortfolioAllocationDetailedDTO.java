package com.activest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioAllocationDetailedDTO(
    String typeName,
    String colorHex,
    BigDecimal totalValue,
    BigDecimal percentage,
    List<CategoryAllocation> categories
) {
    public record CategoryAllocation(
        String categoryName,
        BigDecimal value,
        BigDecimal percentage
    ) {}
}
