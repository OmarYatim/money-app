package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.ConnectionRequiringActionResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.SyncStatusResponse;
import com.moneyapp.backend.banking.mapper.AccountMapper;
import com.moneyapp.backend.sync.entity.SyncEvent;
import com.moneyapp.backend.sync.enums.SyncEventStatus;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import com.moneyapp.backend.sync.service.DataSyncService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConnectionStatusService {

  private final CurrentAppUserService currentAppUserService;
  private final PowensClient powensClient;
  private final UserConnectionService userConnectionService;
  private final SyncEventRepository syncEventRepository;
  private final DataSyncService dataSyncService;

  @Transactional
  public SyncStatusResponse getStatus(String email) {
    return refreshAndBuildStatus(currentAppUserService.resolveExisting(email));
  }

  @Transactional
  public SyncStatusResponse syncNow(String email) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    if (!isBlank(appUser.getPowensToken())) {
      dataSyncService.sync(appUser, SyncEventTrigger.MANUAL, null);
    }

    return refreshAndBuildStatus(appUser);
  }

  private SyncStatusResponse refreshAndBuildStatus(AppUser appUser) {
    if (isBlank(appUser.getPowensToken())) {
      return SyncStatusResponse.builder()
          .connectionsRequiringAction(List.of())
          .hasSyncError(false)
          .build();
    }

    refreshConnectionStates(appUser);
    List<ConnectionRequiringActionResponse> connectionsRequiringAction =
        userConnectionService.findConnectionsRequiringAction(appUser.getId()).stream()
            .map(AccountMapper::toConnectionRequiringActionResponse)
            .toList();
    return SyncStatusResponse.builder()
        .lastSyncedAt(lastSuccessfulSync(appUser.getId()))
        .connectionsRequiringAction(connectionsRequiringAction)
        .hasSyncError(!connectionsRequiringAction.isEmpty() || latestSyncFailed(appUser.getId()))
        .build();
  }

  private Instant lastSuccessfulSync(Long userId) {
    return syncEventRepository
        .findFirstByUserIdAndStatusOrderByTriggeredAtDesc(userId, SyncEventStatus.SUCCESS)
        .map(SyncEvent::getCompletedAt)
        .orElse(null);
  }

  private boolean latestSyncFailed(Long userId) {
    return syncEventRepository
        .findFirstByUserIdOrderByTriggeredAtDesc(userId)
        .map(syncEvent -> SyncEventStatus.FAILED.equals(syncEvent.getStatus()))
        .orElse(false);
  }

  private void refreshConnectionStates(AppUser appUser) {
    PowensConnectionsResponse response = powensClient.fetchConnections(appUser.getPowensToken());
    if (response == null || response.connections() == null) {
      return;
    }

    response.connections().stream()
        .filter(connection -> connection.id() != null)
        .forEach(connection -> updateConnectionState(appUser.getId(), connection));
  }

  private void updateConnectionState(Long userId, PowensConnectionResponse connection) {
    userConnectionService.updateConnectionState(userId, connection.id(), connection.state());
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
