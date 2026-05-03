package com.moneyapp.backend.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DashboardSummaryResponse(
    BigDecimal netWorth,
    BigDecimal totalAssets,
    BigDecimal totalLiabilities,
    BigDecimal futureBalance,
    BigDecimal monthlyIncome,
    BigDecimal monthlyExpenses,
    BigDecimal dailySpending,
    LocalDateTime lastSyncedAt) {}
