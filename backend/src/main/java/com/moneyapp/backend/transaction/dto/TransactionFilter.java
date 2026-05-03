package com.moneyapp.backend.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionFilter(
    Long accountId,
    String category,
    LocalDate minDate,
    LocalDate maxDate,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    String keyword) {}
