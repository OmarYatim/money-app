package com.moneyapp.backend.transaction.dto;

import java.math.BigDecimal;

public record TransactionSummaryResponse(
    long totalElements,
    long unreviewedCount,
    BigDecimal totalIn,
    BigDecimal totalOut,
    BigDecimal net) {}
