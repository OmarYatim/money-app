package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.banking.dto.SyncStatusResponse;
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
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback"
    })
@ActiveProfiles("test")
class ConnectionStatusServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private UserConnectionRepository userConnectionRepository;

  @Autowired private UserConnectionService userConnectionService;

  @BeforeEach
  void setUp() {
    userConnectionRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void getStatusReturnsConnectionsRequiringAction() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("permanent-token")
                .powensUserId("powens-user-id")
                .build());
    ConnectionStatusService service =
        new ConnectionStatusService(
            appUserRepository,
            new StubPowensClient(
                new PowensConnectionsResponse(
                    List.of(new PowensConnectionResponse(123L, "wrongpass")))),
            userConnectionService);

    SyncStatusResponse response = service.getStatus(appUser.getEmail());

    assertThat(response.connectionsRequiringAction()).hasSize(1);
    assertThat(response.connectionsRequiringAction().get(0).connectionId()).isEqualTo(123L);
    assertThat(response.connectionsRequiringAction().get(0).state()).isEqualTo("wrongpass");
  }

  private record StubPowensClient(PowensConnectionsResponse response) implements PowensClient {

    @Override
    public PowensAccessTokenResponse createUserAccessToken() {
      throw new UnsupportedOperationException("Not needed in this test");
    }

    @Override
    public PowensTokenCodeResponse createTemporaryCode(String permanentAccessToken) {
      throw new UnsupportedOperationException("Not needed in this test");
    }

    @Override
    public PowensAccountsResponse fetchAccounts(String permanentAccessToken) {
      throw new UnsupportedOperationException("Not needed in this test");
    }

    @Override
    public PowensConnectionsResponse fetchConnections(String permanentAccessToken) {
      return response;
    }
  }
}
