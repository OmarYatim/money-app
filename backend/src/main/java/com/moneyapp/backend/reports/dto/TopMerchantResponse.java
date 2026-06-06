package com.moneyapp.backend.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TopMerchantResponse(
    String merchant,
    String category,
    long transactionCount,
    BigDecimal totalAmount,
    LocalDate lastTransactionDate) {}
