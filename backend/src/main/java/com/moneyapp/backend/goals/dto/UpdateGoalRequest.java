package com.moneyapp.backend.goals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGoalRequest(
    @NotBlank(message = "name is required") String name,
    @NotNull(message = "targetAmount is required")
        @Positive(message = "targetAmount must be greater than 0")
        BigDecimal targetAmount,
    LocalDate targetDate,
    Long linkedAccountId) {}
