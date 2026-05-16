package com.moneyapp.backend.sync.repository;

import com.moneyapp.backend.sync.entity.SyncEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncEventRepository extends JpaRepository<SyncEvent, Long> {

  List<SyncEvent> findAllByUserIdOrderByTriggeredAtDesc(Long userId);
}
