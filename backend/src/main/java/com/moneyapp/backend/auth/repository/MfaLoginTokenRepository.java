package com.moneyapp.backend.auth.repository;

import com.moneyapp.backend.auth.entity.MfaLoginToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaLoginTokenRepository extends JpaRepository<MfaLoginToken, Long> {

  Optional<MfaLoginToken> findByTokenHash(String tokenHash);

  void deleteByUserId(Long userId);

  void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
