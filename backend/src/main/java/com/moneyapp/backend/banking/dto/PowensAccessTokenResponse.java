package com.moneyapp.backend.banking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PowensAccessTokenResponse(
    @JsonProperty("access_token") String accessToken, PowensUserResponse user) {}
