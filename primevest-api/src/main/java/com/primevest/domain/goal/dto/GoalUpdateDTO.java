package com.primevest.domain.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.primevest.domain.goal.GoalStatus;

public record GoalUpdateDTO(
    String name,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    LocalDate targetDate,
    GoalStatus status
) {}
