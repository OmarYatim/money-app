package com.moneyapp.backend.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "powens")
public record PowensProperties(
    @NotBlank String domain,
    @NotBlank String clientId,
    @NotBlank String clientSecret,
    @NotBlank String manageToken,
    @NotBlank String redirectUrl) {}
