package com.moneyapp.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String frontendUrl, List<String> corsAllowedOrigins, JwtProperties jwt) {

  public record JwtProperties(String secret, long expirationMs) {}
}
