package com.primevest.domain.strategy.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StrategyDTO(
    UUID id,
    Long assetTypeId,
    String assetTypeName,
    BigDecimal targetPercentage,
    boolean includeReservation
) {}
