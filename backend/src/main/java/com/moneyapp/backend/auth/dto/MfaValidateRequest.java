package com.moneyapp.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaValidateRequest(
    @NotBlank @Pattern(regexp = "\\d{6}", message = "code must be 6 digits") String code,
    @NotBlank String mfaToken) {}
