package com.moneyapp.backend.goals.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GoalContributionResponse(
    Long id, Long goalId, BigDecimal amount, String note, LocalDate contributedAt) {}
