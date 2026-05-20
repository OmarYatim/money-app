package com.moneyapp.backend.sync.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AsyncDataSyncService {

  private final DataSyncService dataSyncService;

  @Async
  public void syncAsync(AppUser appUser, SyncEventTrigger triggeredBy, Long connectionId) {
    dataSyncService.sync(appUser, triggeredBy, connectionId);
  }
}
