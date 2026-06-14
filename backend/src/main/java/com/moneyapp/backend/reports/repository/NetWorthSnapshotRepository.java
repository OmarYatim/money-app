package com.moneyapp.backend.reports.repository;

import com.moneyapp.backend.reports.entity.NetWorthSnapshot;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, Long> {

  boolean existsByUserIdAndSnapshotDate(Long userId, LocalDate snapshotDate);

  List<NetWorthSnapshot> findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
      Long userId, LocalDate startDate, LocalDate endDate);

  void deleteByUserId(Long userId);
}
