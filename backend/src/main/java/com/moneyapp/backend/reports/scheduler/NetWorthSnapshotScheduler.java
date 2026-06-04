package com.moneyapp.backend.reports.scheduler;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.reports.service.NetWorthSnapshotService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NetWorthSnapshotScheduler {

  private final AppUserRepository appUserRepository;
  private final NetWorthSnapshotService netWorthSnapshotService;

  @Scheduled(cron = "0 0 0 * * *")
  public void createDailySnapshots() {
    LocalDate today = LocalDate.now();
    appUserRepository.findAll().forEach(user -> createSnapshotIfMissing(user, today));
  }

  private void createSnapshotIfMissing(AppUser user, LocalDate snapshotDate) {
    if (netWorthSnapshotService.createSnapshotIfMissing(user, snapshotDate)) {
      log.info("Created net worth snapshot for userId={} date={}", user.getId(), snapshotDate);
    }
  }
}
