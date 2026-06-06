package com.moneyapp.backend.reports.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.reports.dto.IncomeExpensesResponse;
import com.moneyapp.backend.reports.dto.NetWorthHistoryResponse;
import com.moneyapp.backend.reports.dto.SpendingByCategoryResponse;
import com.moneyapp.backend.reports.dto.TopMerchantResponse;
import com.moneyapp.backend.reports.entity.NetWorthSnapshot;
import com.moneyapp.backend.reports.repository.NetWorthSnapshotRepository;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
class ReportServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private TransactionRepository transactionRepository;

  @Autowired private NetWorthSnapshotRepository netWorthSnapshotRepository;

  @Autowired private ReportService reportService;

  @BeforeEach
  void setUp() {
    netWorthSnapshotRepository.deleteAll();
    transactionRepository.deleteAll();
    accountRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void spendingByCategoryGroupsExpensesSortedDescendingWithPercentages() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    LocalDate april = LocalDate.of(2026, 4, 1);
    repeatTransactions(appUser.getId(), april, "GROCERIES", "-40", 5, 1);
    repeatTransactions(appUser.getId(), april, "DINING", "-50", 3, 10);
    transactionRepository.save(transaction(appUser.getId(), 99L, april, "TRANSPORT", "-30"));
    transactionRepository.save(transaction(appUser.getId(), 100L, april, "INCOME", "1000"));

    List<SpendingByCategoryResponse> result =
        reportService.spendingByCategory(
            appUser.getEmail(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null);

    assertThat(result).hasSize(3);
    assertThat(result.get(0).category()).isEqualTo("GROCERIES");
    assertThat(result.get(0).totalAmount()).isEqualByComparingTo("200");
    assertThat(result.get(0).percentage()).isEqualByComparingTo("52.6");
    assertThat(result)
        .extracting(SpendingByCategoryResponse::category)
        .containsExactly("GROCERIES", "DINING", "TRANSPORT");
    assertThat(
            result.stream()
                .map(SpendingByCategoryResponse::percentage)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isBetween(BigDecimal.valueOf(99), BigDecimal.valueOf(101));
  }

  @Test
  void spendingByCategoryRespectsAccountFilter() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Account accountA = accountRepository.save(account(appUser.getId(), 1L, "Checking"));
    Account accountB = accountRepository.save(account(appUser.getId(), 2L, "Savings"));
    LocalDate april = LocalDate.of(2026, 4, 1);
    transactionRepository.save(
        transaction(appUser.getId(), accountA.getId(), 1L, april, "GROCERIES", "-100"));
    transactionRepository.save(
        transaction(appUser.getId(), accountB.getId(), 2L, april, "DINING", "-200"));

    List<SpendingByCategoryResponse> filtered =
        reportService.spendingByCategory(
            appUser.getEmail(),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 4, 30),
            accountA.getId());
    List<SpendingByCategoryResponse> allAccounts =
        reportService.spendingByCategory(
            appUser.getEmail(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null);

    assertThat(filtered)
        .singleElement()
        .extracting(SpendingByCategoryResponse::category)
        .isEqualTo("GROCERIES");
    assertThat(allAccounts)
        .extracting(SpendingByCategoryResponse::category)
        .containsExactly("DINING", "GROCERIES");
  }

  @Test
  void incomeVsExpensesReturnsChronologicalMonthsIncludingEmptyMonths() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    YearMonth thisMonth = YearMonth.now();
    YearMonth firstMonth = thisMonth.minusMonths(5);
    transactionRepository.save(
        transaction(appUser.getId(), 1L, firstMonth.atDay(2), "INCOME", "1000"));
    transactionRepository.save(
        transaction(appUser.getId(), 2L, firstMonth.atDay(3), "GROCERIES", "-250"));
    transactionRepository.save(
        transaction(appUser.getId(), 3L, thisMonth.atDay(1), "INCOME", "2000"));
    transactionRepository.save(
        transaction(appUser.getId(), 4L, thisMonth.atDay(2), "DINING", "-75"));

    List<IncomeExpensesResponse> result =
        reportService.incomeVsExpenses(appUser.getEmail(), 6, null);

    assertThat(result).hasSize(6);
    assertThat(result.get(0).month()).isEqualTo(firstMonth.toString());
    assertThat(result.get(0).totalIncome()).isEqualByComparingTo("1000");
    assertThat(result.get(0).totalExpenses()).isEqualByComparingTo("250");
    assertThat(result.get(0).netCashFlow()).isEqualByComparingTo("750");
    assertThat(result.get(1).totalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get(1).totalExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.get(5).month()).isEqualTo(thisMonth.toString());
  }

  @Test
  void netWorthHistoryReturnsSnapshotsChronologically() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    LocalDate today = LocalDate.now();
    netWorthSnapshotRepository.save(snapshot(appUser.getId(), today.minusDays(1), "200"));
    netWorthSnapshotRepository.save(snapshot(appUser.getId(), today.minusDays(3), "100"));
    netWorthSnapshotRepository.save(snapshot(appUser.getId(), today.minusDays(2), "150"));

    List<NetWorthHistoryResponse> result = reportService.netWorthHistory(appUser.getEmail(), 1);

    assertThat(result).hasSize(3);
    assertThat(result).extracting(NetWorthHistoryResponse::date).isSorted();
    assertThat(result.get(0).netWorth()).isEqualByComparingTo("100");
    assertThat(result.get(1).netWorth()).isEqualByComparingTo("150");
    assertThat(result.get(2).netWorth()).isEqualByComparingTo("200");
  }

  @Test
  void topMerchantsGroupsExpensesByMerchantSortedDescending() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Account accountA = accountRepository.save(account(appUser.getId(), 1L, "Checking"));
    Account accountB = accountRepository.save(account(appUser.getId(), 2L, "Savings"));
    LocalDate april = LocalDate.of(2026, 4, 1);
    transactionRepository.save(
        transaction(appUser.getId(), accountA.getId(), 1L, april, "Carrefour", "-45"));
    transactionRepository.save(
        transaction(appUser.getId(), accountA.getId(), 2L, april.plusDays(2), "Carrefour", "-55"));
    transactionRepository.save(
        transaction(appUser.getId(), accountB.getId(), 3L, april.plusDays(1), "Netflix", "-20"));
    transactionRepository.save(
        transaction(appUser.getId(), accountA.getId(), 4L, april.plusDays(3), "Salary", "2000"));

    List<TopMerchantResponse> result =
        reportService.topMerchants(
            appUser.getEmail(), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), null, 8);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).merchant()).isEqualTo("Carrefour");
    assertThat(result.get(0).transactionCount()).isEqualTo(2);
    assertThat(result.get(0).totalAmount()).isEqualByComparingTo("100");
    assertThat(result.get(0).lastTransactionDate()).isEqualTo(april.plusDays(2));
    assertThat(result.get(1).merchant()).isEqualTo("Netflix");
  }

  @Test
  void topMerchantsRespectsAccountFilterAndLimit() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    Account accountA = accountRepository.save(account(appUser.getId(), 1L, "Checking"));
    Account accountB = accountRepository.save(account(appUser.getId(), 2L, "Savings"));
    LocalDate april = LocalDate.of(2026, 4, 1);
    transactionRepository.save(
        transaction(appUser.getId(), accountA.getId(), 1L, april, "Carrefour", "-45"));
    transactionRepository.save(
        transaction(appUser.getId(), accountA.getId(), 2L, april, "Monoprix", "-30"));
    transactionRepository.save(
        transaction(appUser.getId(), accountB.getId(), 3L, april, "Netflix", "-200"));

    List<TopMerchantResponse> result =
        reportService.topMerchants(
            appUser.getEmail(),
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 4, 30),
            accountA.getId(),
            1);

    assertThat(result)
        .singleElement()
        .extracting(TopMerchantResponse::merchant)
        .isEqualTo("Carrefour");
  }

  private void repeatTransactions(
      Long userId, LocalDate date, String category, String value, int count, long externalIdStart) {
    for (int i = 0; i < count; i++) {
      transactionRepository.save(
          transaction(userId, externalIdStart + i, date.plusDays(i), category, value));
    }
  }

  private Account account(Long userId, Long externalAccountId, String name) {
    return Account.builder()
        .userId(userId)
        .externalAccountId(externalAccountId)
        .name(name)
        .balance(BigDecimal.ZERO)
        .coming(BigDecimal.ZERO)
        .currency("EUR")
        .build();
  }

  private Transaction transaction(
      Long userId, Long externalTransactionId, LocalDate date, String category, String value) {
    return transaction(userId, null, externalTransactionId, date, category, value);
  }

  private Transaction transaction(
      Long userId,
      Long accountId,
      Long externalTransactionId,
      LocalDate date,
      String category,
      String value) {
    return Transaction.builder()
        .userId(userId)
        .accountId(accountId)
        .externalTransactionId(externalTransactionId)
        .date(date)
        .label(category)
        .value(new BigDecimal(value))
        .category(category)
        .build();
  }

  private NetWorthSnapshot snapshot(Long userId, LocalDate date, String netWorth) {
    return NetWorthSnapshot.builder()
        .userId(userId)
        .snapshotDate(date)
        .netWorth(new BigDecimal(netWorth))
        .build();
  }
}
