package com.moneyapp.backend.goals.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateContributionRequest(
    @NotNull(message = "amount is required") @Positive(message = "amount must be greater than 0")
        BigDecimal amount,
    String note,
    LocalDate contributedAt) {}
