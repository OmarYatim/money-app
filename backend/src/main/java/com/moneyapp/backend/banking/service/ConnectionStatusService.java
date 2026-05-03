package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.dto.PowensConnectionResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.SyncStatusResponse;
import com.moneyapp.backend.banking.mapper.AccountMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConnectionStatusService {

  private final AppUserRepository appUserRepository;
  private final PowensClient powensClient;
  private final UserConnectionService userConnectionService;

  @Transactional
  public SyncStatusResponse getStatus(String email) {
    return appUserRepository
        .findByEmail(email)
        .map(this::refreshAndBuildStatus)
        .orElseGet(
            () -> SyncStatusResponse.builder().connectionsRequiringAction(List.of()).build());
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
