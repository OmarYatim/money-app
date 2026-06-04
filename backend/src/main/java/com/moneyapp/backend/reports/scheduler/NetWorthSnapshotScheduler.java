package com.moneyapp.backend.reports.scheduler;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.dashboard.service.DashboardSummaryService;
import com.moneyapp.backend.reports.entity.NetWorthSnapshot;
import com.moneyapp.backend.reports.repository.NetWorthSnapshotRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class NetWorthSnapshotScheduler {

  private final AppUserRepository appUserRepository;
  private final DashboardSummaryService dashboardSummaryService;
  private final NetWorthSnapshotRepository netWorthSnapshotRepository;

  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void createDailySnapshots() {
    LocalDate today = LocalDate.now();
    appUserRepository.findAll().forEach(user -> createSnapshotIfMissing(user, today));
  }

  private void createSnapshotIfMissing(AppUser user, LocalDate snapshotDate) {
    if (netWorthSnapshotRepository.existsByUserIdAndSnapshotDate(user.getId(), snapshotDate)) {
      return;
    }

    netWorthSnapshotRepository.save(
        NetWorthSnapshot.builder()
            .userId(user.getId())
            .snapshotDate(snapshotDate)
            .netWorth(dashboardSummaryService.computeNetWorth(user.getId()))
            .build());
    log.info("Created net worth snapshot for userId={} date={}", user.getId(), snapshotDate);
  }
}
