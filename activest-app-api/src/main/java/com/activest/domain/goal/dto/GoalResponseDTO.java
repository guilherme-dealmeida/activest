package com.activest.domain.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.activest.domain.goal.GoalStatus;

public record GoalResponseDTO(
    UUID id,
    String name,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    BigDecimal progressPercentage,
    LocalDate targetDate,
    GoalStatus status,
    LocalDateTime createdAt
) {}
