package com.moneyapp.backend.transaction.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PowensTransactionResponse(
    Long id,
    @JsonProperty("id_account") Long accountId,
    LocalDate date,
    String label,
    String wording,
    @JsonProperty("original_wording") String originalWording,
    @JsonProperty("application_date") @JsonAlias({"rdate", "value_date"}) LocalDate applicationDate,
    BigDecimal value,
    @JsonProperty("id_category") Integer idCategory,
    String type,
    PowensCounterparty counterparty) {

  public PowensTransactionResponse(
      Long id,
      Long accountId,
      LocalDate date,
      String label,
      String wording,
      BigDecimal value,
      Integer idCategory,
      String type) {
    this(id, accountId, date, label, wording, null, null, value, idCategory, type, null);
  }

  public record PowensCounterparty(String label) {}
}
