package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioPerformanceDTO(
    BigDecimal totalInvested,
    BigDecimal currentValue,
    BigDecimal totalDividends,
    BigDecimal totalReturn,
    BigDecimal returnPercentage,
    List<AssetPerformance> assets
) {
    public record AssetPerformance(
        String ticker,
        BigDecimal invested,
        BigDecimal currentValue,
        BigDecimal returnPct
    ) {}
}
