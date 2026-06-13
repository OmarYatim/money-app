package com.moneyapp.backend.sync.service;

import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataRetentionScheduler {

  private static final int SYNC_EVENT_RETENTION_DAYS = 90;

  private final SyncEventRepository syncEventRepository;
  private final RefreshTokenRepository refreshTokenRepository;

  @Scheduled(cron = "0 0 2 * * *")
  @Transactional
  public void runRetentionCleanup() {
    LocalDateTime now = LocalDateTime.now();
    long deletedEvents =
        syncEventRepository.deleteByCreatedAtBefore(now.minusDays(SYNC_EVENT_RETENTION_DAYS));
    long deletedTokens = refreshTokenRepository.deleteByExpiresAtBefore(now);

    log.info(
        "Purged {} sync_event rows older than {} days", deletedEvents, SYNC_EVENT_RETENTION_DAYS);
    log.info("Purged {} expired refresh_token rows", deletedTokens);
  }
}
