package com.moneyapp.backend.sync.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.banking.entity.UserConnection;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import com.moneyapp.backend.banking.service.AccountService;
import com.moneyapp.backend.reports.service.NetWorthSnapshotService;
import com.moneyapp.backend.sync.entity.SyncEvent;
import com.moneyapp.backend.sync.enums.SyncEventStatus;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import com.moneyapp.backend.transaction.service.TransactionService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@RequiredArgsConstructor
public class DataSyncService {

  private static final int MAX_ATTEMPTS = 3;
  private static final long MAX_RATE_LIMIT_RETRY_MINUTES = 60;

  private final SyncEventRepository syncEventRepository;
  private final UserConnectionRepository userConnectionRepository;
  private final AccountService accountService;
  private final TransactionService transactionService;
  private final TaskScheduler taskScheduler;
  private final NetWorthSnapshotService netWorthSnapshotService;

  public SyncEvent sync(AppUser appUser, SyncEventTrigger triggeredBy, Long connectionId) {
    return sync(appUser, triggeredBy, connectionId, 1);
  }

  private SyncEvent sync(
      AppUser appUser, SyncEventTrigger triggeredBy, Long connectionId, int attemptCount) {
    SyncEvent syncEvent =
        syncEventRepository.save(
            SyncEvent.builder()
                .userId(appUser.getId())
                .connectionId(connectionId)
                .triggeredBy(triggeredBy)
                .triggeredAt(Instant.now())
                .status(SyncEventStatus.PENDING)
                .attemptCount(attemptCount)
                .build());

    try {
      AccountService.AccountSyncResult accountSyncResult = accountService.syncAccounts(appUser);
      transactionService.syncTransactions(appUser, accountSyncResult.ibans());
      netWorthSnapshotService.createSnapshotIfMissing(appUser);
      syncEvent.setStatus(SyncEventStatus.SUCCESS);
      syncEvent.setCompletedAt(Instant.now());
      syncEvent.setErrorMessage(null);
    } catch (RuntimeException exception) {
      syncEvent.setStatus(SyncEventStatus.FAILED);
      syncEvent.setCompletedAt(Instant.now());
      syncEvent.setErrorMessage(safeErrorMessage(exception));
      if (attemptCount >= MAX_ATTEMPTS) {
        markConnectionSyncFailed(appUser.getId(), connectionId);
      } else {
        scheduleRetry(appUser, triggeredBy, connectionId, attemptCount, exception);
      }
    }

    return syncEventRepository.save(syncEvent);
  }

  private void scheduleRetry(
      AppUser appUser,
      SyncEventTrigger triggeredBy,
      Long connectionId,
      int attemptCount,
      RuntimeException exception) {
    Duration retryDelay = retryDelay(attemptCount);
    if (isRateLimitException(exception)
        && retryDelay.compareTo(Duration.ofMinutes(MAX_RATE_LIMIT_RETRY_MINUTES)) > 0) {
      return;
    }

    taskScheduler.schedule(
        () -> sync(appUser, triggeredBy, connectionId, attemptCount + 1),
        Instant.now().plus(retryDelay));
  }

  private Duration retryDelay(int attemptCount) {
    return Duration.ofMinutes((long) Math.pow(2, attemptCount));
  }

  private boolean isRateLimitException(RuntimeException exception) {
    return exception instanceof WebClientResponseException.TooManyRequests
        || (exception instanceof WebClientResponseException webClientException
            && webClientException.getStatusCode().value() == 429);
  }

  private void markConnectionSyncFailed(Long userId, Long connectionId) {
    List<UserConnection> connections =
        connectionId == null
            ? userConnectionRepository.findByUserIdAndStatus(userId, UserConnection.STATUS_ACTIVE)
            : userConnectionRepository.findByUserIdAndConnectionId(userId, connectionId).stream()
                .toList();

    connections.forEach(connection -> connection.setStatus(UserConnection.STATUS_SYNC_FAILED));
    userConnectionRepository.saveAll(connections);
  }

  private String safeErrorMessage(RuntimeException exception) {
    if (exception instanceof WebClientResponseException webClientException) {
      return "Powens request failed with HTTP " + webClientException.getStatusCode().value();
    }

    return exception.getMessage();
  }
}
