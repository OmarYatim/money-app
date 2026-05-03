package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

  private final AppProperties appProperties;

  public String generateToken(String email) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(email)
        .issuedAt(new Date(now))
        .expiration(new Date(now + appProperties.jwt().expirationMs()))
        .signWith(secretKey())
        .compact();
  }

  public Optional<String> extractEmail(String token) {
    try {
      Claims claims =
          Jwts.parser().verifyWith(secretKey()).build().parseSignedClaims(token).getPayload();
      return Optional.of(claims.getSubject());
    } catch (JwtException | IllegalArgumentException e) {
      log.warn("Invalid JWT: {}", e.getMessage());
      return Optional.empty();
    }
  }

  private SecretKey secretKey() {
    return Keys.hmacShaKeyFor(appProperties.jwt().secret().getBytes(StandardCharsets.UTF_8));
  }
}
