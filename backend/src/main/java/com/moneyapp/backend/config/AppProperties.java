package com.moneyapp.backend.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
    String frontendUrl,
    List<String> corsAllowedOrigins,
    JwtProperties jwt,
    MailProperties mail,
    AuthProperties auth,
    RateLimitProperties rateLimit) {

  public record JwtProperties(String secret, long expirationMs) {}

  public record MailProperties(String from) {}

  public record AuthProperties(boolean refreshCookieSecure) {}

  public record RateLimitProperties(
      boolean enabled,
      EndpointRateLimitProperties login,
      EndpointRateLimitProperties refresh,
      LoginFailureProperties loginFailure) {}

  public record EndpointRateLimitProperties(int capacity, long refillSeconds) {}

  public record LoginFailureProperties(int threshold, long delaySeconds) {}
}
