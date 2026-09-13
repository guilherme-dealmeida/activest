package com.primevest.domain.strategy.dto;

import java.math.BigDecimal;
import java.util.List;

public record RebalanceDTO(
    BigDecimal totalPortfolioValue,
    List<RebalanceItem> items
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
}
