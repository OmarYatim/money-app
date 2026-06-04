package com.moneyapp.backend.reports.dto;

import java.math.BigDecimal;

public record IncomeExpensesResponse(
    String month, BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal netCashFlow) {}
