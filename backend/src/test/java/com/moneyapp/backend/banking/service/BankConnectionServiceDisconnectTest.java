package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.entity.UserConnection;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

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
class BankConnectionServiceDisconnectTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private UserConnectionRepository userConnectionRepository;

  @Autowired private TransactionRepository transactionRepository;

  @Autowired private CurrentAppUserService currentAppUserService;

  @Autowired private UserConnectionService userConnectionService;

  private CapturingPowensClient powensClient;
  private BankConnectionService service;

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    accountRepository.deleteAll();
    userConnectionRepository.deleteAll();
    appUserRepository.deleteAll();
    powensClient = new CapturingPowensClient();
    service =
        new BankConnectionService(
            null,
            currentAppUserService,
            null,
            userConnectionService,
            null,
            accountRepository,
            null,
            transactionRepository,
            null,
            powensClient,
            null);
  }

  @Test
  void disconnectConnectionSoftDisablesAccountsAndKeepsHistory() {
    AppUser appUser = saveUser("person@example.com", "powens-token");
    UserConnection userConnection = saveConnection(appUser.getId(), 456L);
    Account account = saveAccount(appUser.getId(), 456L);
    Transaction transaction = saveTransaction(appUser.getId(), account.getId());

    service.disconnectConnection(appUser.getEmail(), 456L, false);

    assertThat(powensClient.deletedToken).isEqualTo("powens-token");
    assertThat(powensClient.deletedConnectionId).isEqualTo(456L);
    assertThat(accountRepository.findById(account.getId()))
        .get()
        .extracting(Account::isDisabled)
        .isEqualTo(true);
    assertThat(transactionRepository.findById(transaction.getId())).isPresent();
    assertThat(userConnectionRepository.findById(userConnection.getId())).isPresent();
  }

  @Test
  void disconnectConnectionHardDeletesAccountsTransactionsAndConnection() {
    AppUser appUser = saveUser("person@example.com", "powens-token");
    UserConnection userConnection = saveConnection(appUser.getId(), 456L);
    Account account = saveAccount(appUser.getId(), 456L);
    Transaction transaction = saveTransaction(appUser.getId(), account.getId());

    service.disconnectConnection(appUser.getEmail(), 456L, true);

    assertThat(powensClient.deletedConnectionId).isEqualTo(456L);
    assertThat(transactionRepository.findById(transaction.getId())).isEmpty();
    assertThat(accountRepository.findById(account.getId())).isEmpty();
    assertThat(userConnectionRepository.findById(userConnection.getId())).isEmpty();
  }

  @Test
  void disconnectConnectionRejectsConnectionsOwnedByAnotherUser() {
    AppUser appUser = saveUser("person@example.com", "powens-token");
    AppUser otherUser = saveUser("other@example.com", "other-token");
    saveConnection(otherUser.getId(), 456L);

    assertThatThrownBy(() -> service.disconnectConnection(appUser.getEmail(), 456L, false))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(powensClient.deletedConnectionId).isNull();
  }

  private AppUser saveUser(String email, String powensToken) {
    return appUserRepository.save(
        AppUser.builder().email(email).powensToken(powensToken).powensUserId(email).build());
  }

  private UserConnection saveConnection(Long userId, Long connectionId) {
    return userConnectionRepository.save(
        UserConnection.builder()
            .userId(userId)
            .connectionId(connectionId)
            .status(UserConnection.STATUS_ACTIVE)
            .build());
  }

  private Account saveAccount(Long userId, Long connectionId) {
    return accountRepository.save(
        Account.builder()
            .userId(userId)
            .connectionId(connectionId)
            .externalAccountId(123L)
            .institutionName("Test Bank")
            .name("Main checking")
            .type("checking")
            .balance(BigDecimal.TEN)
            .coming(BigDecimal.ZERO)
            .currency("EUR")
            .build());
  }

  private Transaction saveTransaction(Long userId, Long accountId) {
    return transactionRepository.save(
        Transaction.builder()
            .userId(userId)
            .accountId(accountId)
            .externalAccountId(123L)
            .externalTransactionId(987L)
            .date(LocalDate.of(2026, 5, 24))
            .label("Card payment")
            .value(BigDecimal.ONE.negate())
            .type("debit")
            .category("OTHER")
            .build());
  }

  private static class CapturingPowensClient implements PowensClient {

    private String deletedToken;
    private Long deletedConnectionId;

    @Override
    public void deleteConnection(String permanentAccessToken, Long connectionId) {
      deletedToken = permanentAccessToken;
      deletedConnectionId = connectionId;
    }

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
      throw new UnsupportedOperationException("Not needed in this test");
    }
  }
}
