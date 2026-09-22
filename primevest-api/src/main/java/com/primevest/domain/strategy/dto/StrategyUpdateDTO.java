package com.primevest.domain.strategy.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import java.util.List;

public record StrategyUpdateDTO(
    List<StrategyItemDTO> items
) {
    public record StrategyItemDTO(
        Long assetTypeId,
        @DecimalMin("0") @DecimalMax("100") BigDecimal targetPercentage
    ) {}
}
