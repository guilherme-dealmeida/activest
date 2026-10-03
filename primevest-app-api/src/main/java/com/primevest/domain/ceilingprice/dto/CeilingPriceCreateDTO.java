package com.primevest.domain.ceilingprice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CeilingPriceCreateDTO(
    @NotBlank String ticker,
    @NotNull @Positive BigDecimal maxPrice,
    boolean alertEnabled
) {}
