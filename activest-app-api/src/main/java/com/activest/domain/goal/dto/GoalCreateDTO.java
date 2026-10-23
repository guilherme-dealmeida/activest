package com.activest.domain.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalCreateDTO(
    @NotBlank String name,
    @NotNull BigDecimal targetAmount,
    LocalDate targetDate
) {}
