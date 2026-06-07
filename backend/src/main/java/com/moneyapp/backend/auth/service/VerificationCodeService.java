package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {

  private static final int CODE_BOUND = 1_000_000;

  private final AppProperties appProperties;
  private final SecureRandom secureRandom = new SecureRandom();

  public String generateCode() {
    return "%06d".formatted(secureRandom.nextInt(CODE_BOUND));
  }

  public String hash(String code, String email) {
    try {
      String payload = code + ":" + email.toLowerCase() + ":" + appProperties.jwt().secret();
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
