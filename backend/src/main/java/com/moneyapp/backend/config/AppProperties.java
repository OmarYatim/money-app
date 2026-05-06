package com.moneyapp.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String frontendUrl, JwtProperties jwt) {

  public record JwtProperties(String secret, long expirationMs) {}
}
