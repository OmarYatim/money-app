package com.moneyapp.backend.goals.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalProgressResponse(
    Long id,
    String name,
    BigDecimal targetAmount,
    LocalDate targetDate,
    String icon,
    String color,
    String category,
    String priority,
    String note,
    boolean autoSaveEnabled,
    BigDecimal plannedMonthlyContribution,
    BigDecimal currentAmount,
    BigDecimal progressPercent,
    BigDecimal monthlyRate,
    LocalDate projectedCompletionDate,
    boolean archived,
    Long linkedAccountId,
    String linkedAccountName,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
