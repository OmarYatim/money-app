package com.moneyapp.backend.transaction.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TransactionResponse(
    Long id,
    Long accountId,
    String accountName,
    LocalDate date,
    String label,
    String wording,
    String originalWording,
    BigDecimal value,
    LocalDate applicationDate,
    String category,
    boolean categoryOverridden,
    String type,
    String counterpartyLabel,
    boolean internalTransfer,
    boolean internalTransferOverridden,
    boolean reviewed,
    LocalDateTime reviewedAt) {}
