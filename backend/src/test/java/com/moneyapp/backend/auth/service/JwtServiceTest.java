package com.moneyapp.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.config.AppProperties;
import com.moneyapp.backend.config.AppProperties.JwtProperties;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = "test-secret-key-must-be-at-least-32-chars!!";
  private static final long EXPIRATION_MS = 900_000L;

  private final JwtService jwtService =
      new JwtService(
          new AppProperties(
              "http://localhost:4200",
              List.of("http://localhost:4200"),
              new JwtProperties(SECRET, EXPIRATION_MS),
              new AppProperties.MailProperties("no-reply@test.nexioo.local")));

  @Test
  void generateTokenReturnsNonBlankJwt() {
    String token = jwtService.generateToken("user@example.com");
    assertThat(token).isNotBlank();
  }

  @Test
  void extractEmailRoundTripsCorrectly() {
    String token = jwtService.generateToken("user@example.com");
    Optional<String> email = jwtService.extractEmail(token);
    assertThat(email).contains("user@example.com");
  }

  @Test
  void extractEmailReturnsEmptyForTamperedToken() {
    String token = jwtService.generateToken("user@example.com");
    String tampered = token.substring(0, token.length() - 4) + "xxxx";
    assertThat(jwtService.extractEmail(tampered)).isEmpty();
  }

  @Test
  void extractEmailReturnsEmptyForGarbage() {
    assertThat(jwtService.extractEmail("not.a.jwt")).isEmpty();
  }
}
