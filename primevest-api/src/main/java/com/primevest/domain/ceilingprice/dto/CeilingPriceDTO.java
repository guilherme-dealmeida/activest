package com.primevest.domain.ceilingprice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CeilingPriceDTO(
    UUID id,
    String ticker,
    String assetName,
    BigDecimal maxPrice,
    BigDecimal currentPrice,
    BigDecimal diffPercentage,
    boolean alertEnabled,
    boolean belowCeiling
) {}
