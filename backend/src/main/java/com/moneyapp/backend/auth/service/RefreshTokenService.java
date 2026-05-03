package com.moneyapp.backend.auth.service;

import com.moneyapp.backend.auth.entity.RefreshToken;
import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

  private static final int REFRESH_TOKEN_DAYS = 30;

  private final RefreshTokenRepository refreshTokenRepository;

  @Transactional
  public RefreshToken create(Long userId) {
    return refreshTokenRepository.save(
        RefreshToken.builder()
            .userId(userId)
            .token(UUID.randomUUID().toString())
            .expiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_DAYS))
            .build());
  }

  public Optional<RefreshToken> validate(String rawToken) {
    return refreshTokenRepository
        .findByToken(rawToken)
        .filter(rt -> rt.getExpiresAt().isAfter(LocalDateTime.now()));
  }

  @Transactional
  public void revokeByToken(String rawToken) {
    refreshTokenRepository.deleteByToken(rawToken);
  }

  @Transactional
  public void revokeAllForUser(Long userId) {
    refreshTokenRepository.deleteByUserId(userId);
  }
}
