package com.moneyapp.backend.transaction.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PowensTransactionResponse(
    Long id,
    @JsonProperty("id_account") Long accountId,
    LocalDate date,
    String label,
    String wording,
    BigDecimal value,
    @JsonProperty("id_category") Integer idCategory,
    String type) {}
