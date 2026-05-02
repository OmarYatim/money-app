package com.moneyapp.backend.banking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PowensAccountResponse(
    Long id,
    @JsonProperty("id_connection") @JsonAlias("connection_id") Long connectionId,
    @JsonProperty("institution_name") String institutionName,
    String name,
    String type,
    String iban,
    BigDecimal balance,
    BigDecimal coming,
    String currency,
    @JsonProperty("last_update") LocalDateTime lastUpdate,
    boolean disabled) {}
