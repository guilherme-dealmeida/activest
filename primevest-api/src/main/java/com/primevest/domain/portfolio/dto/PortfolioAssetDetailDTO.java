package com.primevest.domain.portfolio.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PortfolioAssetDetailDTO(
    UUID id,
    String ticker,
    String assetName,
    String typeName,
    String categoryName,
    BigDecimal totalShares,
    BigDecimal averagePrice,
    BigDecimal totalInvested,
    BigDecimal currentValue,
    BigDecimal currentPrice,
    BigDecimal totalDividends,
    BigDecimal valorization,
    BigDecimal returnPercentage,
    boolean isReservation
) {}
