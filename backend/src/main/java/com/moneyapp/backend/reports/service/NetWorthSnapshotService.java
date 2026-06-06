package com.moneyapp.backend.reports.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.dashboard.service.DashboardSummaryService;
import com.moneyapp.backend.reports.entity.NetWorthSnapshot;
import com.moneyapp.backend.reports.repository.NetWorthSnapshotRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NetWorthSnapshotService {

  private final DashboardSummaryService dashboardSummaryService;
  private final NetWorthSnapshotRepository netWorthSnapshotRepository;

  @Transactional
  public boolean createSnapshotIfMissing(AppUser user) {
    return createSnapshotIfMissing(user, LocalDate.now());
  }

  @Transactional
  public boolean createSnapshotIfMissing(AppUser user, LocalDate snapshotDate) {
    if (netWorthSnapshotRepository.existsByUserIdAndSnapshotDate(user.getId(), snapshotDate)) {
      return false;
    }

    netWorthSnapshotRepository.save(
        NetWorthSnapshot.builder()
            .userId(user.getId())
            .snapshotDate(snapshotDate)
            .netWorth(dashboardSummaryService.computeNetWorth(user.getId()))
            .build());
    return true;
  }
}
