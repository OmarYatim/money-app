package com.moneyapp.backend.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record AccountResponse(
    Long id,
    Long connectionId,
    String institutionName,
    String name,
    String type,
    String accountNumberLastFour,
    BigDecimal balance,
    BigDecimal coming,
    String currency,
    LocalDateTime lastUpdate,
    boolean disabled) {}
