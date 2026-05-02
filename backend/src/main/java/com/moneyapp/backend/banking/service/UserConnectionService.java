package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.banking.entity.UserConnection;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserConnectionService {

  private final UserConnectionRepository userConnectionRepository;

  @Transactional
  public List<UserConnection> upsertActiveConnections(Long userId, List<Long> connectionIds) {
    return connectionIds.stream()
        .map(connectionId -> upsertActiveConnection(userId, connectionId))
        .toList();
  }

  @Transactional
  public UserConnection updateConnectionState(Long userId, Long connectionId, String state) {
    UserConnection userConnection =
        userConnectionRepository
            .findByUserIdAndConnectionId(userId, connectionId)
            .orElseGet(
                () ->
                    UserConnection.builder()
                        .userId(userId)
                        .connectionId(connectionId)
                        .status(UserConnection.STATUS_ACTIVE)
                        .build());

    userConnection.setState(state);
    userConnection.setStatus(
        requiresAction(state)
            ? UserConnection.STATUS_REQUIRING_ACTION
            : UserConnection.STATUS_ACTIVE);
    return userConnectionRepository.save(userConnection);
  }

  @Transactional(readOnly = true)
  public List<UserConnection> findConnectionsRequiringAction(Long userId) {
    return userConnectionRepository.findByUserIdAndStatus(
        userId, UserConnection.STATUS_REQUIRING_ACTION);
  }

  private UserConnection upsertActiveConnection(Long userId, Long connectionId) {
    UserConnection userConnection =
        userConnectionRepository
            .findByUserIdAndConnectionId(userId, connectionId)
            .orElseGet(
                () ->
                    UserConnection.builder()
                        .userId(userId)
                        .connectionId(connectionId)
                        .status(UserConnection.STATUS_ACTIVE)
                        .build());

    userConnection.setStatus(UserConnection.STATUS_ACTIVE);
    userConnection.setState(null);
    return userConnectionRepository.save(userConnection);
  }

  private boolean requiresAction(String state) {
    if (state == null || state.isBlank()) {
      return false;
    }

    return List.of("SCARequired", "webauthRequired", "additionalInformationNeeded", "wrongpass")
        .contains(state);
  }
}
