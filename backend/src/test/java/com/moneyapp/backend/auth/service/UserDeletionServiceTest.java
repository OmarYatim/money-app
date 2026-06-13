package com.moneyapp.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.entity.MfaLoginToken;
import com.moneyapp.backend.auth.entity.PendingRegistration;
import com.moneyapp.backend.auth.entity.RefreshToken;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.repository.MfaLoginTokenRepository;
import com.moneyapp.backend.auth.repository.PendingRegistrationRepository;
import com.moneyapp.backend.auth.repository.RefreshTokenRepository;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.entity.BankConnectionState;
import com.moneyapp.backend.banking.entity.UserConnection;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.banking.repository.BankConnectionStateRepository;
import com.moneyapp.backend.banking.repository.UserConnectionRepository;
import com.moneyapp.backend.banking.service.PowensClient;
import com.moneyapp.backend.goals.entity.Goal;
import com.moneyapp.backend.goals.entity.GoalContribution;
import com.moneyapp.backend.goals.repository.GoalContributionRepository;
import com.moneyapp.backend.goals.repository.GoalRepository;
import com.moneyapp.backend.reports.entity.NetWorthSnapshot;
import com.moneyapp.backend.reports.repository.NetWorthSnapshotRepository;
import com.moneyapp.backend.sync.entity.SyncEvent;
import com.moneyapp.backend.sync.enums.SyncEventStatus;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.repository.SyncEventRepository;
import com.moneyapp.backend.transaction.dto.PowensTransactionsResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class UserDeletionServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private RefreshTokenRepository refreshTokenRepository;

  @Autowired private MfaLoginTokenRepository mfaLoginTokenRepository;

  @Autowired private PendingRegistrationRepository pendingRegistrationRepository;

  @Autowired private SyncEventRepository syncEventRepository;

  @Autowired private BankConnectionStateRepository bankConnectionStateRepository;

  @Autowired private GoalContributionRepository goalContributionRepository;

  @Autowired private GoalRepository goalRepository;

  @Autowired private NetWorthSnapshotRepository netWorthSnapshotRepository;

  @Autowired private TransactionRepository transactionRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private UserConnectionRepository userConnectionRepository;

  @Autowired private UserDeletionService userDeletionService;

  @Autowired private CapturingPowensClient powensClient;

  @BeforeEach
  void setUp() {
    goalContributionRepository.deleteAll();
    goalRepository.deleteAll();
    transactionRepository.deleteAll();
    accountRepository.deleteAll();
    netWorthSnapshotRepository.deleteAll();
    userConnectionRepository.deleteAll();
    bankConnectionStateRepository.deleteAll();
    syncEventRepository.deleteAll();
    refreshTokenRepository.deleteAll();
    mfaLoginTokenRepository.deleteAll();
    pendingRegistrationRepository.deleteAll();
    appUserRepository.deleteAll();
    powensClient.reset();
  }

  @Test
  void deleteAuthenticatedUserRevokesPowensAndDeletesAllUserData() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("delete-me@example.com")
                .powensToken("powens-token")
                .powensUserId("powens-user-id")
                .build());
    saveUserData(appUser);

    userDeletionService.deleteAuthenticatedUser(appUser.getEmail());

    assertThat(powensClient.deletedToken).isEqualTo("powens-token");
    assertThat(powensClient.deletedPowensUserId).isEqualTo("powens-user-id");
    assertThat(powensClient.userExistedWhenRevoked).isTrue();
    assertThat(refreshTokenRepository.findAll()).isEmpty();
    assertThat(mfaLoginTokenRepository.findAll()).isEmpty();
    assertThat(pendingRegistrationRepository.findAll()).isEmpty();
    assertThat(syncEventRepository.findAll()).isEmpty();
    assertThat(bankConnectionStateRepository.findAll()).isEmpty();
    assertThat(goalContributionRepository.findAll()).isEmpty();
    assertThat(goalRepository.findAll()).isEmpty();
    assertThat(netWorthSnapshotRepository.findAll()).isEmpty();
    assertThat(transactionRepository.findAll()).isEmpty();
    assertThat(accountRepository.findAll()).isEmpty();
    assertThat(userConnectionRepository.findAll()).isEmpty();
    assertThat(appUserRepository.findById(appUser.getId())).isEmpty();
  }

  private void saveUserData(AppUser appUser) {
    refreshTokenRepository.save(
        RefreshToken.builder()
            .userId(appUser.getId())
            .token("refresh-token")
            .expiresAt(LocalDateTime.now().plusDays(1))
            .build());
    mfaLoginTokenRepository.save(
        MfaLoginToken.builder()
            .userId(appUser.getId())
            .tokenHash("mfa-token-hash")
            .expiresAt(LocalDateTime.now().plusMinutes(5))
            .build());
    pendingRegistrationRepository.save(
        PendingRegistration.builder()
            .email(appUser.getEmail())
            .firstName("Delete")
            .lastName("Me")
            .phone("+33123456789")
            .passwordHash("password-hash")
            .codeHash("code-hash")
            .expiresAt(LocalDateTime.now().plusMinutes(10))
            .build());
    syncEventRepository.save(
        SyncEvent.builder()
            .userId(appUser.getId())
            .triggeredBy(SyncEventTrigger.MANUAL)
            .triggeredAt(Instant.now())
            .status(SyncEventStatus.SUCCESS)
            .build());
    bankConnectionStateRepository.save(
        BankConnectionState.builder().userId(appUser.getId()).state("bank-state").build());
    userConnectionRepository.save(
        UserConnection.builder()
            .userId(appUser.getId())
            .connectionId(456L)
            .status(UserConnection.STATUS_ACTIVE)
            .build());
    Account account =
        accountRepository.save(
            Account.builder()
                .userId(appUser.getId())
                .connectionId(456L)
                .externalAccountId(123L)
                .name("Checking")
                .balance(BigDecimal.TEN)
                .coming(BigDecimal.ZERO)
                .currency("EUR")
                .build());
    transactionRepository.save(
        Transaction.builder()
            .userId(appUser.getId())
            .accountId(account.getId())
            .externalAccountId(123L)
            .externalTransactionId(789L)
            .date(LocalDate.now())
            .label("Card payment")
            .value(BigDecimal.ONE.negate())
            .category("OTHER")
            .build());
    Goal goal =
        goalRepository.save(
            Goal.builder()
                .userId(appUser.getId())
                .linkedAccountId(account.getId())
                .name("Emergency fund")
                .targetAmount(BigDecimal.valueOf(1000))
                .build());
    goalContributionRepository.save(
        GoalContribution.builder()
            .goalId(goal.getId())
            .amount(BigDecimal.TEN)
            .contributedAt(LocalDate.now())
            .build());
    netWorthSnapshotRepository.save(
        NetWorthSnapshot.builder()
            .userId(appUser.getId())
            .snapshotDate(LocalDate.now())
            .netWorth(BigDecimal.TEN)
            .build());
  }

  @TestConfiguration
  static class UserDeletionServiceTestConfig {

    @Bean
    @Primary
    CapturingPowensClient powensClient(AppUserRepository appUserRepository) {
      return new CapturingPowensClient(appUserRepository);
    }
  }

  static class CapturingPowensClient implements PowensClient {

    private final AppUserRepository appUserRepository;
    private String deletedToken;
    private String deletedPowensUserId;
    private boolean userExistedWhenRevoked;

    CapturingPowensClient(AppUserRepository appUserRepository) {
      this.appUserRepository = appUserRepository;
    }

    @Override
    public void deleteUser(String permanentAccessToken, String powensUserId) {
      deletedToken = permanentAccessToken;
      deletedPowensUserId = powensUserId;
      userExistedWhenRevoked = appUserRepository.findByPowensUserId(powensUserId).isPresent();
    }

    private void reset() {
      deletedToken = null;
      deletedPowensUserId = null;
      userExistedWhenRevoked = false;
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

    @Override
    public PowensTransactionsResponse fetchTransactions(String permanentAccessToken) {
      throw new UnsupportedOperationException("Not needed in this test");
    }
  }
}
