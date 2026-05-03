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
import com.moneyapp.backend.transaction.dto.TransactionFilter;
import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
  void findTransactionsReturnsPaginatedTransactionsSortedNewestFirstWithAccountName() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Account checking =
        accountRepository.save(
            Account.builder()
                .userId(appUser.getId())
                .externalAccountId(456L)
                .name("Main checking")
                .balance(BigDecimal.ZERO)
                .coming(BigDecimal.ZERO)
                .currency("EUR")
                .build());
    transactionRepository.save(
        transaction(
            appUser.getId(),
            checking.getId(),
            1L,
            LocalDate.of(2026, 4, 1),
            "Older",
            "Older payment",
            "-20",
            "GROCERIES"));
    transactionRepository.save(
        transaction(
            appUser.getId(),
            checking.getId(),
            2L,
            LocalDate.of(2026, 5, 1),
            "Newer",
            "Newer payment",
            "-30",
            "DINING"));
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    Page<TransactionResponse> response =
        service.findTransactions(
            appUser.getEmail(),
            emptyFilter(),
            PageRequest.of(0, 1, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"))));

    assertThat(response.getContent()).hasSize(1);
    assertThat(response.getTotalElements()).isEqualTo(2);
    assertThat(response.getTotalPages()).isEqualTo(2);
    assertThat(response.getContent().get(0).label()).isEqualTo("Newer");
    assertThat(response.getContent().get(0).accountName()).isEqualTo("Main checking");
  }

  @Test
  void findTransactionsFiltersByKeywordCategoryDateAndAmount() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    transactionRepository.save(
        transaction(
            appUser.getId(),
            null,
            1L,
            LocalDate.of(2026, 4, 1),
            "AMAZON MARKETPLACE",
            "Card payment",
            "-80",
            "SHOPPING"));
    transactionRepository.save(
        transaction(
            appUser.getId(),
            null,
            2L,
            LocalDate.of(2026, 4, 15),
            "Amazon digital",
            "Monthly subscription",
            "-12",
            "SUBSCRIPTION"));
    transactionRepository.save(
        transaction(
            appUser.getId(),
            null,
            3L,
            LocalDate.of(2026, 5, 1),
            "NETFLIX",
            "Streaming",
            "-20",
            "SUBSCRIPTION"));
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    Page<TransactionResponse> response =
        service.findTransactions(
            appUser.getEmail(),
            new TransactionFilter(
                null,
                "SHOPPING",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30),
                BigDecimal.valueOf(-100),
                BigDecimal.valueOf(-50),
                "amaz"),
            PageRequest.of(0, 20, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"))));

    assertThat(response.getContent())
        .singleElement()
        .extracting(TransactionResponse::label)
        .isEqualTo("AMAZON MARKETPLACE");
    assertThat(response.getTotalElements()).isEqualTo(1);
  }

  @Test
  void findTransactionsRejectsInvalidCategory() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(
            () ->
                service.findTransactions(
                    appUser.getEmail(),
                    new TransactionFilter(null, "INVALID", null, null, null, null, null),
                    PageRequest.of(0, 20)))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("INVALID is not a valid category");
  }

  @Test
  void findTransactionsOnlyReturnsAuthenticatedUsersTransactions() {
    AppUser owner = appUserRepository.save(AppUser.builder().email("owner@example.com").build());
    AppUser other = appUserRepository.save(AppUser.builder().email("other@example.com").build());
    transactionRepository.save(
        transaction(
            owner.getId(),
            null,
            1L,
            LocalDate.of(2026, 4, 1),
            "Owner transaction",
            null,
            "-80",
            "SHOPPING"));
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    Page<TransactionResponse> response =
        service.findTransactions(other.getEmail(), emptyFilter(), PageRequest.of(0, 20));

    assertThat(response.getContent()).isEmpty();
    assertThat(response.getTotalElements()).isZero();
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
                        "CB MARKET PARIS",
                        LocalDate.of(2026, 5, 4),
                        BigDecimal.valueOf(-42.50),
                        2,
                        "card",
                        new PowensTransactionResponse.PowensCounterparty(
                            "Market SARL"))))); // 2 → GROCERIES

    List<Transaction> transactions = service.syncTransactions(appUser, Set.of());

    assertThat(transactions).hasSize(1);
    Transaction transaction = transactions.get(0);
    assertThat(transaction.getExternalTransactionId()).isEqualTo(123L);
    assertThat(transaction.getAccountId()).isEqualTo(account.getId());
    assertThat(transaction.getExternalAccountId()).isEqualTo(456L);
    assertThat(transaction.getValue()).isEqualByComparingTo("-42.50");
    assertThat(transaction.getOriginalWording()).isEqualTo("CB MARKET PARIS");
    assertThat(transaction.getApplicationDate()).isEqualTo(LocalDate.of(2026, 5, 4));
    assertThat(transaction.getCounterpartyLabel()).isEqualTo("Market SARL");
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

    Transaction transaction = service.syncTransactions(appUser, Set.of()).get(0);

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

    Transaction transaction = service.syncTransactions(appUser, Set.of()).get(0);

    assertThat(transaction.getCategory()).isEqualTo("DINING");
    assertThat(transaction.isCategoryOverridden()).isTrue();
  }

  @Test
  void syncTransactionsDoesNotOverwriteReviewedState() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("permanent-token")
                .powensUserId("powens-user-id")
                .build());
    LocalDateTime reviewedAt = LocalDateTime.of(2026, 5, 3, 10, 30);
    transactionRepository.save(
        Transaction.builder()
            .userId(appUser.getId())
            .externalTransactionId(123L)
            .date(LocalDate.of(2026, 5, 2))
            .label("Dinner")
            .value(BigDecimal.valueOf(-25))
            .category("DINING")
            .reviewed(true)
            .reviewedAt(reviewedAt)
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
                        null))));

    Transaction transaction = service.syncTransactions(appUser, Set.of()).get(0);

    assertThat(transaction.isReviewed()).isTrue();
    assertThat(transaction.getReviewedAt()).isEqualTo(reviewedAt);
  }

  @Test
  void syncTransactionsRequiresPowensIdentity() {
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(() -> service.syncTransactions(AppUser.builder().id(1L).build(), Set.of()))
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
  void updateInternalTransferSetsOverrideFlagAndPreventsFutureAutoDetection() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Transaction transaction =
        transactionRepository.save(
            Transaction.builder()
                .userId(appUser.getId())
                .externalTransactionId(1L)
                .date(LocalDate.of(2026, 5, 1))
                .label("Salary")
                .value(BigDecimal.valueOf(2000))
                .category("OTHER")
                .type("transfer")
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    TransactionResponse response =
        service.updateInternalTransfer("person@example.com", transaction.getId(), true);

    assertThat(response.internalTransfer()).isTrue();
    assertThat(response.internalTransferOverridden()).isTrue();
    Transaction saved = transactionRepository.findById(transaction.getId()).orElseThrow();
    assertThat(saved.isInternalTransfer()).isTrue();
    assertThat(saved.isInternalTransferOverridden()).isTrue();
  }

  @Test
  void updateReviewedSetsReviewedAtWhenReviewed() {
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
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    TransactionResponse response =
        service.updateReviewed("person@example.com", transaction.getId(), true);

    assertThat(response.reviewed()).isTrue();
    assertThat(response.reviewedAt()).isNotNull();
    Transaction saved = transactionRepository.findById(transaction.getId()).orElseThrow();
    assertThat(saved.isReviewed()).isTrue();
    assertThat(saved.getReviewedAt()).isNotNull();
  }

  @Test
  void updateReviewedClearsReviewedAtWhenUnreviewed() {
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
                .reviewed(true)
                .reviewedAt(LocalDateTime.of(2026, 5, 2, 10, 0))
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    TransactionResponse response =
        service.updateReviewed("person@example.com", transaction.getId(), false);

    assertThat(response.reviewed()).isFalse();
    assertThat(response.reviewedAt()).isNull();
    Transaction saved = transactionRepository.findById(transaction.getId()).orElseThrow();
    assertThat(saved.isReviewed()).isFalse();
    assertThat(saved.getReviewedAt()).isNull();
  }

  @Test
  void updateReviewedRejectsOtherUsersTransaction() {
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
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(() -> service.updateReviewed("other@example.com", transaction.getId(), true))
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

    service.syncTransactions(appUser, Set.of());

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

  @Test
  void syncTransactionsDetectsInternalTransferByIbanDigitsInWording() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("token")
                .powensUserId("powens-id")
                .build());
    accountRepository.save(
        Account.builder()
            .userId(appUser.getId())
            .externalAccountId(10L)
            .name("Checking")
            .balance(BigDecimal.ZERO)
            .coming(BigDecimal.ZERO)
            .currency("EUR")
            .build());
    accountRepository.save(
        Account.builder()
            .userId(appUser.getId())
            .externalAccountId(20L)
            .name("Savings")
            .balance(BigDecimal.ZERO)
            .coming(BigDecimal.ZERO)
            .currency("EUR")
            .build());
    // savingsIban: "FR7698765432109876543210" → digits: "7698765432109876543210"
    // Wording of debit contains "9876543210987654321" which is substring of those digits
    String savingsIban = "FR7698765432109876543210";
    TransactionService service =
        transactionService(
            new PowensTransactionsResponse(
                List.of(
                    new PowensTransactionResponse(
                        1L,
                        10L,
                        LocalDate.of(2026, 5, 1),
                        "Transfer to savings",
                        "9876543210987654321 MOTIF: Epargne",
                        BigDecimal.valueOf(-200),
                        null,
                        "transfer"),
                    new PowensTransactionResponse(
                        2L,
                        20L,
                        LocalDate.of(2026, 5, 1),
                        "Transfer received",
                        "VIREMENT RECU",
                        BigDecimal.valueOf(200),
                        null,
                        "transfer"),
                    new PowensTransactionResponse(
                        3L,
                        10L,
                        LocalDate.of(2026, 5, 1),
                        "Coffee",
                        null,
                        BigDecimal.valueOf(-5),
                        2,
                        "card"))));

    service.syncTransactions(appUser, Set.of(savingsIban));

    Transaction debit =
        transactionRepository
            .findByUserIdAndExternalTransactionId(appUser.getId(), 1L)
            .orElseThrow();
    Transaction credit =
        transactionRepository
            .findByUserIdAndExternalTransactionId(appUser.getId(), 2L)
            .orElseThrow();
    Transaction coffee =
        transactionRepository
            .findByUserIdAndExternalTransactionId(appUser.getId(), 3L)
            .orElseThrow();

    assertThat(debit.isInternalTransfer()).isTrue();
    assertThat(credit.isInternalTransfer()).isTrue();
    assertThat(coffee.isInternalTransfer()).isFalse();
  }

  @Test
  void syncTransactionsPreservesManualInternalTransferOverride() {
    AppUser appUser =
        appUserRepository.save(
            AppUser.builder()
                .email("person@example.com")
                .powensToken("token")
                .powensUserId("powens-id")
                .build());
    transactionRepository.save(
        Transaction.builder()
            .userId(appUser.getId())
            .externalTransactionId(1L)
            .date(LocalDate.of(2026, 5, 1))
            .label("Salary")
            .value(BigDecimal.valueOf(2000))
            .category("OTHER")
            .type("transfer")
            .internalTransfer(false)
            .internalTransferOverridden(true)
            .build());
    TransactionService service =
        transactionService(
            new PowensTransactionsResponse(
                List.of(
                    new PowensTransactionResponse(
                        1L,
                        null,
                        LocalDate.of(2026, 5, 1),
                        "Salary",
                        "9876543210987654321 MOTIF: Paie",
                        BigDecimal.valueOf(2000),
                        null,
                        "transfer"))));

    service.syncTransactions(appUser, Set.of("FR7698765432109876543210"));

    Transaction transaction =
        transactionRepository
            .findByUserIdAndExternalTransactionId(appUser.getId(), 1L)
            .orElseThrow();
    assertThat(transaction.isInternalTransfer()).isFalse();
    assertThat(transaction.isInternalTransferOverridden()).isTrue();
  }

  @Test
  void getTransactionReturnsFullDetailForOwner() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
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
    Transaction transaction =
        transactionRepository.save(
            Transaction.builder()
                .userId(appUser.getId())
                .accountId(account.getId())
                .externalTransactionId(123L)
                .date(LocalDate.of(2026, 5, 3))
                .applicationDate(LocalDate.of(2026, 5, 4))
                .label("Market")
                .wording("Supermarket card payment")
                .originalWording("CB MARKET PARIS")
                .value(BigDecimal.valueOf(-42.50))
                .type("card")
                .category("GROCERIES")
                .categoryOverridden(true)
                .reviewed(true)
                .reviewedAt(LocalDateTime.of(2026, 5, 4, 10, 30))
                .counterpartyLabel("Market SARL")
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    TransactionResponse response =
        service.getTransaction("person@example.com", transaction.getId());

    assertThat(response.id()).isEqualTo(transaction.getId());
    assertThat(response.accountId()).isEqualTo(account.getId());
    assertThat(response.accountName()).isEqualTo("Main checking");
    assertThat(response.label()).isEqualTo("Market");
    assertThat(response.wording()).isEqualTo("Supermarket card payment");
    assertThat(response.originalWording()).isEqualTo("CB MARKET PARIS");
    assertThat(response.value()).isEqualByComparingTo("-42.50");
    assertThat(response.date()).isEqualTo(LocalDate.of(2026, 5, 3));
    assertThat(response.applicationDate()).isEqualTo(LocalDate.of(2026, 5, 4));
    assertThat(response.type()).isEqualTo("card");
    assertThat(response.category()).isEqualTo("GROCERIES");
    assertThat(response.categoryOverridden()).isTrue();
    assertThat(response.reviewed()).isTrue();
    assertThat(response.reviewedAt()).isEqualTo(LocalDateTime.of(2026, 5, 4, 10, 30));
    assertThat(response.counterpartyLabel()).isEqualTo("Market SARL");
  }

  @Test
  void getTransactionRejectsOtherUsersTransaction() {
    AppUser owner = appUserRepository.save(AppUser.builder().email("owner@example.com").build());
    appUserRepository.save(AppUser.builder().email("other@example.com").build());
    Transaction transaction =
        transactionRepository.save(
            Transaction.builder()
                .userId(owner.getId())
                .externalTransactionId(123L)
                .date(LocalDate.of(2026, 5, 3))
                .label("Market")
                .value(BigDecimal.valueOf(-42.50))
                .category("GROCERIES")
                .build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(() -> service.getTransaction("other@example.com", transaction.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("403 FORBIDDEN");
  }

  @Test
  void getTransactionReturnsNotFoundForMissingTransaction() {
    appUserRepository.save(AppUser.builder().email("person@example.com").build());
    TransactionService service = transactionService(new PowensTransactionsResponse(List.of()));

    assertThatThrownBy(() -> service.getTransaction("person@example.com", 999L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");
  }

  private TransactionService transactionService(PowensTransactionsResponse response) {
    return new TransactionService(
        transactionRepository,
        accountRepository,
        currentAppUserService,
        new StubPowensClient(response),
        new CategoryMappingService());
  }

  private TransactionFilter emptyFilter() {
    return new TransactionFilter(null, null, null, null, null, null, null);
  }

  private Transaction transaction(
      Long userId,
      Long accountId,
      Long externalTransactionId,
      LocalDate date,
      String label,
      String wording,
      String value,
      String category) {
    return Transaction.builder()
        .userId(userId)
        .accountId(accountId)
        .externalTransactionId(externalTransactionId)
        .date(date)
        .label(label)
        .wording(wording)
        .value(new BigDecimal(value))
        .category(category)
        .build();
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
