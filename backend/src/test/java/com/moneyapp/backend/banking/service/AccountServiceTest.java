package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.dto.AccountResponse;
import com.moneyapp.backend.banking.dto.PowensAccessTokenResponse;
import com.moneyapp.backend.banking.dto.PowensAccountResponse;
import com.moneyapp.backend.banking.dto.PowensAccountsResponse;
import com.moneyapp.backend.banking.dto.PowensConnectionsResponse;
import com.moneyapp.backend.banking.dto.PowensTokenCodeResponse;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback",
      "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
      "app.jwt.expiration-ms=900000"
    })
@ActiveProfiles("test")
class AccountServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private CurrentAppUserService currentAppUserService;

  @BeforeEach
  void setUp() {
    accountRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void syncAccountsStoresMappedAccountsWithoutFullIban() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("permanent-token")
                .powensUserId("powens-user-id")
                .build());
    AccountService service =
        new AccountService(
            currentAppUserService,
            accountRepository,
            new StubPowensClient(
                new PowensAccountsResponse(
                    List.of(
                        new PowensAccountResponse(
                            123L,
                            456L,
                            "Test Bank",
                            "Main checking",
                            "checking",
                            "FR7612345678901234",
                            BigDecimal.valueOf(1200),
                            BigDecimal.valueOf(25),
                            new PowensAccountResponse.PowensCurrency("EUR", "€"),
                            LocalDateTime.of(2026, 5, 2, 12, 0),
                            false)))));

    List<Account> accounts = service.syncAccounts(appUser);

    assertThat(accounts).hasSize(1);
    Account account = accounts.get(0);
    assertThat(account.getExternalAccountId()).isEqualTo(123L);
    assertThat(account.getConnectionId()).isEqualTo(456L);
    assertThat(account.getInstitutionName()).isEqualTo("Test Bank");
    assertThat(account.getAccountNumberLastFour()).isEqualTo("1234");
    assertThat(account.getBalance()).isEqualByComparingTo("1200");
    assertThat(account.getComing()).isEqualByComparingTo("25");
  }

  @Test
  void findAccountsReturnsDtosForExistingUser() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    accountRepository.save(
        Account.builder()
            .userId(appUser.getId())
            .externalAccountId(123L)
            .connectionId(456L)
            .institutionName("Test Bank")
            .name("Main checking")
            .type("checking")
            .accountNumberLastFour("1234")
            .balance(BigDecimal.TEN)
            .coming(BigDecimal.ZERO)
            .currency("EUR")
            .build());
    AccountService service =
        new AccountService(
            currentAppUserService,
            accountRepository,
            new StubPowensClient(new PowensAccountsResponse(List.of())));

    List<AccountResponse> accounts = service.findAccounts("person@example.com");

    assertThat(accounts).hasSize(1);
    assertThat(accounts.get(0).name()).isEqualTo("Main checking");
    assertThat(accounts.get(0).accountNumberLastFour()).isEqualTo("1234");
  }

  @Test
  void syncAccountsRequiresPowensIdentity() {
    AccountService service =
        new AccountService(
            currentAppUserService,
            accountRepository,
            new StubPowensClient(new PowensAccountsResponse(List.of())));

    assertThatThrownBy(() -> service.syncAccounts(AppUser.builder().id(1L).build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Powens user identity is required before syncing accounts");
  }

  @Test
  void findAccountsThrows401WhenEmailNotFound() {
    AccountService service =
        new AccountService(
            currentAppUserService,
            accountRepository,
            new StubPowensClient(new PowensAccountsResponse(List.of())));

    assertThatThrownBy(() -> service.findAccounts("nobody@example.com"))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
        .hasMessageContaining("401 UNAUTHORIZED");
  }

  private record StubPowensClient(PowensAccountsResponse response) implements PowensClient {

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
      return response;
    }

    @Override
    public PowensConnectionsResponse fetchConnections(String permanentAccessToken) {
      throw new UnsupportedOperationException("Not needed in this test");
    }
  }
}
