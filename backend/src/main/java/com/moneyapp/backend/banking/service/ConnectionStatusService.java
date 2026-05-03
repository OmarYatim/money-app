package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.PowensConnectionResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.SyncStatusResponse;
import com.moneyapp.backend.banking.mapper.AccountMapper;
import com.moneyapp.backend.transaction.service.TransactionService;
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
  private final AccountService accountService;
  private final TransactionService transactionService;

  @Transactional
  public SyncStatusResponse getStatus(String email) {
    return refreshAndBuildStatus(currentAppUserService.resolveExisting(email));
  }

  @Transactional
  public SyncStatusResponse syncNow(String email) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    if (!isBlank(appUser.getPowensToken())) {
      AccountService.AccountSyncResult syncResult = accountService.syncAccounts(appUser);
      transactionService.syncTransactions(appUser, syncResult.ibans());
    }

    return refreshAndBuildStatus(appUser);
  }

  private SyncStatusResponse refreshAndBuildStatus(AppUser appUser) {
    if (isBlank(appUser.getPowensToken())) {
      return SyncStatusResponse.builder().connectionsRequiringAction(List.of()).build();
    }

    refreshConnectionStates(appUser);
    return SyncStatusResponse.builder()
        .connectionsRequiringAction(
            userConnectionService.findConnectionsRequiringAction(appUser.getId()).stream()
                .map(AccountMapper::toConnectionRequiringActionResponse)
                .toList())
        .build();
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
