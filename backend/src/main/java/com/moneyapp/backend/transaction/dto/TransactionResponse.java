package com.moneyapp.backend.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionResponse(
    Long id,
    LocalDate date,
    String label,
    String wording,
    BigDecimal value,
    String category,
    boolean categoryOverridden) {}
