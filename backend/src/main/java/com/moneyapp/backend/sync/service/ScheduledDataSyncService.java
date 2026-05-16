package com.moneyapp.backend.sync.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.sync.entity.SyncEvent;
import com.moneyapp.backend.sync.enums.SyncEventStatus;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ScheduledDataSyncService {

  private final AppUserRepository appUserRepository;
  private final SyncEventRepository syncEventRepository;
  private final AsyncDataSyncService asyncDataSyncService;

  @Value("${app.sync.freshness-threshold-hours:12}")
  private long freshnessThresholdHours;

  @Scheduled(cron = "${app.sync.cron}")
  public void syncStaleUsers() {
    Instant now = Instant.now();
    appUserRepository.findUsersWithActiveConnections().stream()
        .filter(appUser -> isSyncDue(appUser, now))
        .forEach(
            appUser -> asyncDataSyncService.syncAsync(appUser, SyncEventTrigger.SCHEDULED, null));
  }

  boolean isSyncDue(AppUser appUser, Instant now) {
    return syncEventRepository
        .findFirstByUserIdAndStatusOrderByTriggeredAtDesc(appUser.getId(), SyncEventStatus.SUCCESS)
        .map(SyncEvent::getCompletedAt)
        .map(lastSuccess -> lastSuccess.isBefore(now.minus(syncFreshnessThreshold())))
        .orElse(true);
  }

  private Duration syncFreshnessThreshold() {
    return Duration.ofHours(freshnessThresholdHours);
  }
}
