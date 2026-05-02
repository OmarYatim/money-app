package com.moneyapp.backend.banking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PowensUserResponse(
    @JsonProperty("id") @JsonAlias({"id_user", "user_id"}) String id) {}
