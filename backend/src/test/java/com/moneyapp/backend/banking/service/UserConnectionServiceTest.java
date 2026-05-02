package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.banking.entity.UserConnection;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.moneyapp.me/api/bank/callback"
    })
@ActiveProfiles("test")
class UserConnectionServiceTest {

  @Autowired private UserConnectionRepository userConnectionRepository;

  @Autowired private UserConnectionService userConnectionService;

  @BeforeEach
  void setUp() {
    userConnectionRepository.deleteAll();
  }

  @Test
  void upsertActiveConnectionsCreatesConnectionRows() {
    userConnectionService.upsertActiveConnections(1L, List.of(123L, 456L));

    List<UserConnection> userConnections = userConnectionRepository.findByUserId(1L);
    assertThat(userConnections)
        .extracting(UserConnection::getConnectionId)
        .containsExactlyInAnyOrder(123L, 456L);
    assertThat(userConnections).allMatch(connection -> connection.getStatus().equals("active"));
  }

  @Test
  void upsertActiveConnectionsDoesNotDuplicateExistingConnection() {
    userConnectionService.upsertActiveConnections(1L, List.of(123L));
    userConnectionService.upsertActiveConnections(1L, List.of(123L));

    assertThat(userConnectionRepository.findByUserId(1L)).hasSize(1);
  }
}
