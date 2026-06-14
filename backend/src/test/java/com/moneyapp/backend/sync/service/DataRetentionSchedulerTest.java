package com.moneyapp.backend.sync.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.entity.RefreshToken;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import com.moneyapp.backend.sync.entity.SyncEvent;
import com.moneyapp.backend.sync.enums.SyncEventStatus;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DataRetentionSchedulerTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Autowired private SyncEventRepository syncEventRepository;

  @Autowired private DataRetentionScheduler dataRetentionScheduler;

  @Autowired private JdbcTemplate jdbcTemplate;

  private AppUser appUser;

  @BeforeEach
  void setUp() {
    refreshTokenRepository.deleteAll();
    syncEventRepository.deleteAll();
    appUserRepository.deleteAll();
    appUser = appUserRepository.save(AppUser.builder().email("retention@example.com").build());
  }

  @Test
  void runRetentionCleanupPurgesOnlyOldSyncEventsAndExpiredRefreshTokens() {
    for (int index = 0; index < 5; index++) {
      SyncEvent oldEvent = syncEventRepository.save(syncEvent());
      setSyncEventCreatedAt(oldEvent.getId(), LocalDateTime.now().minusDays(95));

      SyncEvent recentEvent = syncEventRepository.save(syncEvent());
      setSyncEventCreatedAt(recentEvent.getId(), LocalDateTime.now().minusDays(10));
    }

    refreshTokenRepository.save(refreshToken("expired-1", LocalDateTime.now().minusDays(1)));
    refreshTokenRepository.save(refreshToken("expired-2", LocalDateTime.now().minusMinutes(1)));
    refreshTokenRepository.save(refreshToken("valid", LocalDateTime.now().plusDays(1)));

    dataRetentionScheduler.runRetentionCleanup();

    assertThat(syncEventRepository.findAll()).hasSize(5);
    assertThat(refreshTokenRepository.findAll())
        .singleElement()
        .extracting(RefreshToken::getToken)
        .isEqualTo("valid");
  }

  private SyncEvent syncEvent() {
    return SyncEvent.builder()
        .userId(appUser.getId())
        .triggeredBy(SyncEventTrigger.MANUAL)
        .triggeredAt(Instant.now())
        .status(SyncEventStatus.SUCCESS)
        .build();
  }

  private RefreshToken refreshToken(String token, LocalDateTime expiresAt) {
    return RefreshToken.builder().userId(appUser.getId()).token(token).expiresAt(expiresAt).build();
  }

  private void setSyncEventCreatedAt(Long id, LocalDateTime createdAt) {
    jdbcTemplate.update("update sync_event set created_at = ? where id = ?", createdAt, id);
  }
}
