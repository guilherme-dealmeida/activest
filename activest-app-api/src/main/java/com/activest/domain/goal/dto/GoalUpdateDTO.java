package com.activest.domain.goal.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.activest.domain.goal.GoalStatus;

public record GoalUpdateDTO(
    String name,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    LocalDate targetDate,
    GoalStatus status
) {}
