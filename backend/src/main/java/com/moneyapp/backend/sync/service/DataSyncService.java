package com.moneyapp.backend.sync.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.banking.service.AccountService;
import com.moneyapp.backend.sync.entity.SyncEvent;
import com.moneyapp.backend.sync.enums.SyncEventStatus;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import com.moneyapp.backend.transaction.service.TransactionService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSyncService {

  private final SyncEventRepository syncEventRepository;
  private final AccountService accountService;
  private final TransactionService transactionService;

  public SyncEvent sync(AppUser appUser, SyncEventTrigger triggeredBy, Long connectionId) {
    SyncEvent syncEvent =
        syncEventRepository.save(
            SyncEvent.builder()
                .userId(appUser.getId())
                .connectionId(connectionId)
                .triggeredBy(triggeredBy)
                .triggeredAt(Instant.now())
                .status(SyncEventStatus.PENDING)
                .attemptCount(1)
                .build());

    try {
      AccountService.AccountSyncResult accountSyncResult = accountService.syncAccounts(appUser);
      transactionService.syncTransactions(appUser, accountSyncResult.ibans());
      syncEvent.setStatus(SyncEventStatus.SUCCESS);
      syncEvent.setCompletedAt(Instant.now());
      syncEvent.setErrorMessage(null);
    } catch (RuntimeException exception) {
      syncEvent.setStatus(SyncEventStatus.FAILED);
      syncEvent.setCompletedAt(Instant.now());
      syncEvent.setErrorMessage(exception.getMessage());
    }

    return syncEventRepository.save(syncEvent);
  }
}
