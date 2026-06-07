package com.moneyapp.backend.auth.repository;

import com.moneyapp.backend.auth.entity.PendingRegistration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

  Optional<PendingRegistration> findByEmail(String email);

  void deleteByEmail(String email);

  void deleteByExpiresAtBefore(LocalDateTime expiresAt);
}
