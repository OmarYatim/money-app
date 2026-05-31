package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.banking.dto.SyncStatusResponse;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import com.moneyapp.backend.sync.service.DataSyncService;
import com.moneyapp.backend.transaction.dto.PowensTransactionsResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.service.TransactionService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback",
      "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
      "app.jwt.expiration-ms=900000"
    })
@ActiveProfiles("test")
class ConnectionStatusServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private UserConnectionRepository userConnectionRepository;

  @Autowired private SyncEventRepository syncEventRepository;

  @Autowired private UserConnectionService userConnectionService;

  @Autowired private CurrentAppUserService currentAppUserService;

  @BeforeEach
  void setUp() {
    syncEventRepository.deleteAll();
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
            currentAppUserService,
            new StubPowensClient(
                new PowensConnectionsResponse(
                    List.of(new PowensConnectionResponse(123L, "wrongpass")))),
            userConnectionService,
            syncEventRepository,
            null);

    SyncStatusResponse response = service.getStatus(appUser.getEmail());

    assertThat(response.connectionsRequiringAction()).hasSize(1);
    assertThat(response.connectionsRequiringAction().get(0).connectionId()).isEqualTo(123L);
    assertThat(response.connectionsRequiringAction().get(0).state()).isEqualTo("wrongpass");
  }

  @Test
  void syncNowRefreshesAccountsAndTransactionsBeforeReturningStatus() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("permanent-token")
                .powensUserId("powens-user-id")
                .build());
    FakeAccountService accountService = new FakeAccountService();
    FakeTransactionService transactionService = new FakeTransactionService();
    DataSyncService dataSyncService =
        new DataSyncService(
            syncEventRepository,
            userConnectionRepository,
            accountService,
            transactionService,
            new ConcurrentTaskScheduler());
    ConnectionStatusService service =
        new ConnectionStatusService(
            currentAppUserService,
            new StubPowensClient(new PowensConnectionsResponse(List.of())),
            userConnectionService,
            syncEventRepository,
            dataSyncService);

    SyncStatusResponse response = service.syncNow(appUser.getEmail());

    assertThat(response.connectionsRequiringAction()).isEmpty();
    assertThat(response.lastSyncedAt()).isNotNull();
    assertThat(response.hasSyncError()).isFalse();
    assertThat(accountService.syncedUserId).isEqualTo(appUser.getId());
    assertThat(transactionService.syncedUserId).isEqualTo(appUser.getId());
  }

  private static class FakeAccountService extends AccountService {
    private Long syncedUserId;

    FakeAccountService() {
      super(null, null, null, null);
    }

    @Override
    public AccountSyncResult syncAccounts(AppUser appUser) {
      syncedUserId = appUser.getId();
      return new AccountSyncResult(List.of(), Set.of());
    }
  }

  private static class FakeTransactionService extends TransactionService {
    private Long syncedUserId;

    FakeTransactionService() {
      super(null, null, null, null, null);
    }

    @Override
    public List<Transaction> syncTransactions(AppUser appUser, Set<String> knownIbans) {
      syncedUserId = appUser.getId();
      return List.of();
    }
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

    @Override
    public PowensTransactionsResponse fetchTransactions(String permanentAccessToken) {
      throw new UnsupportedOperationException("Not needed in this test");
    }
  }
}
