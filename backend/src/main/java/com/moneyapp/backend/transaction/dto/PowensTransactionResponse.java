package com.moneyapp.backend.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PowensTransactionResponse(
    Long id,
    @JsonProperty("id_account") Long accountId,
    LocalDate date,
    String label,
    String wording,
    BigDecimal value,
    List<String> categories) {}
