package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.auth.entity.MfaLoginToken;
import com.moneyapp.backend.auth.repository.MfaLoginTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MfaLoginTokenService {

  private static final int TOKEN_BYTES = 32;
  private static final int EXPIRATION_MINUTES = 5;

  private final MfaLoginTokenRepository mfaLoginTokenRepository;
  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public String create(Long userId) {
    mfaLoginTokenRepository.deleteByUserId(userId);
    mfaLoginTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    byte[] rawToken = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(rawToken);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(rawToken);
    mfaLoginTokenRepository.save(
        MfaLoginToken.builder()
            .userId(userId)
            .tokenHash(hash(token))
            .expiresAt(LocalDateTime.now().plusMinutes(EXPIRATION_MINUTES))
            .build());
    return token;
  }

  @Transactional
  public Long consume(String token) {
    MfaLoginToken mfaToken =
        mfaLoginTokenRepository
            .findByTokenHash(hash(token))
            .filter(candidate -> candidate.getUsedAt() == null)
            .filter(candidate -> candidate.getExpiresAt().isAfter(LocalDateTime.now()))
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or expired code"));
    mfaToken.setUsedAt(LocalDateTime.now());
    return mfaToken.getUserId();
  }

  private String hash(String token) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
