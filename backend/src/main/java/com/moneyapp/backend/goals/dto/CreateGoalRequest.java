package com.moneyapp.backend.goals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoalRequest(
    @NotBlank(message = "name is required") String name,
    @NotNull(message = "targetAmount is required")
        @Positive(message = "targetAmount must be greater than 0")
        BigDecimal targetAmount,
    LocalDate targetDate,
    Long linkedAccountId,
    String icon,
    String color,
    String category,
    String priority,
    String note,
    boolean autoSaveEnabled,
    @PositiveOrZero(message = "plannedMonthlyContribution must be greater than or equal to 0")
        BigDecimal plannedMonthlyContribution) {}
