package com.moneyapp.backend.banking.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PowensAccessTokenResponse(
    @JsonProperty("auth_token") @JsonAlias("access_token") String accessToken,
    @JsonProperty("id_user") @JsonAlias({"user_id", "id"}) String userId) {}
