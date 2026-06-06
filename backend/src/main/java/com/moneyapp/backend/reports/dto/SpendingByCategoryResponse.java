package com.moneyapp.backend.reports.dto;

import java.math.BigDecimal;

public record SpendingByCategoryResponse(
    String category, BigDecimal totalAmount, BigDecimal percentage) {}
