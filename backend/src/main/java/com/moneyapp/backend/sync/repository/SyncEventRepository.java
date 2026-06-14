package com.moneyapp.backend.sync.repository;

import com.moneyapp.backend.sync.entity.SyncEvent;
import com.moneyapp.backend.sync.enums.SyncEventStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {

  List<SyncEvent> findAllByUserIdOrderByTriggeredAtDesc(Long userId);

  Optional<SyncEvent> findFirstByUserIdAndStatusOrderByTriggeredAtDesc(
      Long userId, SyncEventStatus status);

  Optional<SyncEvent> findFirstByUserIdOrderByTriggeredAtDesc(Long userId);

  long deleteByCreatedAtBefore(LocalDateTime createdAt);

  void deleteByUserId(Long userId);
}
