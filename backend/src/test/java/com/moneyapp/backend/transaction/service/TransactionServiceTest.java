package com.moneyapp.backend.transaction.service;

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
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.banking.service.PowensClient;
import com.moneyapp.backend.transaction.dto.PowensTransactionResponse;
import com.moneyapp.backend.transaction.dto.PowensTransactionsResponse;
import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class TransactionServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private TransactionRepository transactionRepository;

  @Autowired private CurrentAppUserService currentAppUserService;

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    accountRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void syncTransactionsStoresMappedCategory() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("permanent-token")
                .powensUserId("powens-user-id")
                .build());
    Account account =
        accountRepository.save(
            Account.builder()
                .userId(appUser.getId())
                .externalAccountId(456L)
                .name("Main checking")
                .balance(BigDecimal.ZERO)
                .coming(BigDecimal.ZERO)
                .currency("EUR")
                .build());
    TransactionService service =
        transactionService(
            new PowensTransactionsResponse(
                List.of(
                    new PowensTransactionResponse(
                        123L,
                        456L,
                        LocalDate.of(2026, 5, 3),
                        "Market",
                        "Supermarket card payment",
                        BigDecimal.valueOf(-42.50),
                        2,
                        "card")))); // id_category=2 → GROCERIES

    List<Transaction> transactions = service.syncTransactions(appUser);

    assertThat(transactions).hasSize(1);
    Transaction transaction = transactions.get(0);
    assertThat(transaction.getExternalTransactionId()).isEqualTo(123L);
    assertThat(transaction.getAccountId()).isEqualTo(account.getId());
    assertThat(transaction.getExternalAccountId()).isEqualTo(456L);
    assertThat(transaction.getValue()).isEqualByComparingTo("-42.50");
    assertThat(transaction.getCategory()).isEqualTo("GROCERIES");
    assertThat(transaction.isCategoryOverridden()).isFalse();
  }

  @Test
  void syncTransactionsDefaultsMissingCategoryToOther() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("permanent-token")
                .powensUserId("powens-user-id")
                .build());
    TransactionService service =
        transactionService(
            new PowensTransactionsResponse(
                List.of(
                    new PowensTransactionResponse(
                        123L,
                        null,
                        LocalDate.of(2026, 5, 3),
                        "Unknown",
                        null,
                        BigDecimal.TEN,
                        9998,
                        null)))); // id_category=9998 (Indéfini) → OTHER

    Transaction transaction = service.syncTransactions(appUser).get(0);

    assertThat(transaction.getCategory()).isEqualTo("OTHER");
  }

  @Test
  void syncTransactionsDoesNotOverwriteManualCategoryOverride() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("permanent-token")
                .powensUserId("powens-user-id")
                .build());
    transactionRepository.save(
        Transaction.builder()
            .userId(appUser.getId())
            .externalTransactionId(123L)
            .date(LocalDate.of(2026, 5, 2))
            .label("Dinner")
            .value(BigDecimal.valueOf(-25))
            .category("DINING")
            .categoryOverridden(true)
            .build());
    TransactionService service =
        transactionService(
            new PowensTransactionsResponse(
                List.of(
                    new PowensTransactionResponse(
                        123L,
                        null,
                        LocalDate.of(2026, 5, 3),
                        "Market",
                        "Supermarket card payment",
                        BigDecimal.valueOf(-42.50),
                        2,
                        null)))); // id_category=2 → GROCERIES, but categoryOverridden=true so stays
    // DINING

    Transaction transaction = service.syncTransactions(appUser).get(0);

    assertThat(transaction.getCategory()).isEqualTo("DINING");
    assertThat(transaction.isCategoryOverridden()).isTrue();
  }

  @Test
  void syncTransactionsRequiresPowensIdentity() {
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(() -> service.syncTransactions(AppUser.builder().id(1L).build()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Powens user identity is required before syncing transactions");
  }

  @Test
  void updateCategorySetsOverrideFlag() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Transaction transaction =
        transactionRepository.save(
            Transaction.builder()
                .userId(appUser.getId())
                .externalTransactionId(123L)
                .date(LocalDate.of(2026, 5, 2))
                .label("Market")
                .value(BigDecimal.valueOf(-25))
                .category("GROCERIES")
                .categoryOverridden(false)
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    TransactionResponse response =
        service.updateCategory("person@example.com", transaction.getId(), "DINING");

    assertThat(response.category()).isEqualTo("DINING");
    assertThat(response.categoryOverridden()).isTrue();
    Transaction savedTransaction =
        transactionRepository.findById(transaction.getId()).orElseThrow();
    assertThat(savedTransaction.getCategory()).isEqualTo("DINING");
    assertThat(savedTransaction.isCategoryOverridden()).isTrue();
  }

  @Test
  void updateCategoryRejectsInvalidCategory() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Transaction transaction =
        transactionRepository.save(
            Transaction.builder()
                .userId(appUser.getId())
                .externalTransactionId(123L)
                .date(LocalDate.of(2026, 5, 2))
                .label("Market")
                .value(BigDecimal.valueOf(-25))
                .category("GROCERIES")
                .categoryOverridden(false)
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(
            () ->
                service.updateCategory(
                    "person@example.com", transaction.getId(), "MADE_UP_CATEGORY"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("MADE_UP_CATEGORY is not a valid category");

    Transaction savedTransaction =
        transactionRepository.findById(transaction.getId()).orElseThrow();
    assertThat(savedTransaction.getCategory()).isEqualTo("GROCERIES");
    assertThat(savedTransaction.isCategoryOverridden()).isFalse();
  }

  @Test
  void updateCategoryRejectsOtherUsersTransaction() {
    AppUser owner = appUserRepository.save(AppUser.builder().email("owner@example.com").build());
    appUserRepository.save(AppUser.builder().email("other@example.com").build());
    Transaction transaction =
        transactionRepository.save(
            Transaction.builder()
                .userId(owner.getId())
                .externalTransactionId(123L)
                .date(LocalDate.of(2026, 5, 2))
                .label("Market")
                .value(BigDecimal.valueOf(-25))
                .category("GROCERIES")
                .categoryOverridden(false)
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(
            () -> service.updateCategory("other@example.com", transaction.getId(), "DINING"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  @Test
  void syncTransactionsMarksMatchingTransfersBetweenRegisteredAccountsAsInternal() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("token")
                .powensUserId("powens-id")
                .build());
    Account checking =
        accountRepository.save(
            Account.builder()
                .userId(appUser.getId())
                .externalAccountId(10L)
                .name("Checking")
                .balance(BigDecimal.ZERO)
                .coming(BigDecimal.ZERO)
                .currency("EUR")
                .build());
    Account savings =
        accountRepository.save(
            Account.builder()
                .userId(appUser.getId())
                .externalAccountId(20L)
                .name("Savings")
                .balance(BigDecimal.ZERO)
                .coming(BigDecimal.ZERO)
                .currency("EUR")
                .build());
    TransactionService service =
        transactionService(
            new PowensTransactionsResponse(
                List.of(
                    new PowensTransactionResponse(
                        1L,
                        10L,
                        LocalDate.of(2026, 5, 1),
                        null,
                        "Transfer out",
                        BigDecimal.valueOf(-200),
                        null,
                        "transfer"),
                    new PowensTransactionResponse(
                        2L,
                        20L,
                        LocalDate.of(2026, 5, 1),
                        null,
                        "Transfer in",
                        BigDecimal.valueOf(200),
                        null,
                        "transfer"),
                    new PowensTransactionResponse(
                        3L,
                        10L,
                        LocalDate.of(2026, 5, 1),
                        "Groceries",
                        null,
                        BigDecimal.valueOf(-50),
                        2,
                        "card"))));

    service.syncTransactions(appUser);

    Transaction debit =
        transactionRepository
            .findByUserIdAndExternalTransactionId(appUser.getId(), 1L)
            .orElseThrow();
    Transaction credit =
        transactionRepository
            .findByUserIdAndExternalTransactionId(appUser.getId(), 2L)
            .orElseThrow();
    Transaction groceries =
        transactionRepository
            .findByUserIdAndExternalTransactionId(appUser.getId(), 3L)
            .orElseThrow();

    assertThat(debit.getAccountId()).isEqualTo(checking.getId());
    assertThat(credit.getAccountId()).isEqualTo(savings.getId());
    assertThat(debit.isInternalTransfer()).isTrue();
    assertThat(credit.isInternalTransfer()).isTrue();
    assertThat(groceries.isInternalTransfer()).isFalse();
  }

  private TransactionService transactionService(PowensTransactionsResponse response) {
    return new TransactionService(
        transactionRepository,
        accountRepository,
        currentAppUserService,
        new StubPowensClient(response),
        new CategoryMappingService());
  }

  private record StubPowensClient(PowensTransactionsResponse response) implements PowensClient {

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
      return response;
    }
  }
}
