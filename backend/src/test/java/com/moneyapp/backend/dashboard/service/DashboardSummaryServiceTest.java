package com.moneyapp.backend.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.dashboard.dto.DashboardSummaryResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class DashboardSummaryServiceTest {

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private TransactionRepository transactionRepository;

  @Autowired private DashboardSummaryService dashboardSummaryService;

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    accountRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void computeCalculatesNetWorthAndFutureBalanceFromAccounts() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    LocalDateTime newestSync = LocalDateTime.of(2026, 5, 3, 9, 30);
    accountRepository.save(account(appUser.getId(), 1L, "checking", "1500", "0", newestSync));
    accountRepository.save(
        account(appUser.getId(), 2L, "savings", "3000", "0", newestSync.minusHours(1)));
    accountRepository.save(account(appUser.getId(), 3L, "credit", "-800", "0", newestSync));
    accountRepository.save(account(appUser.getId(), 4L, "loan", "-5000", "0", newestSync));
    accountRepository.save(account(appUser.getId(), 5L, "checking", "2000", "-300", newestSync));
    accountRepository.save(account(appUser.getId(), 6L, "checking", "999", "0", newestSync, true));

    DashboardSummaryResponse summary = dashboardSummaryService.compute(appUser.getEmail());

    assertThat(summary.totalAssets()).isEqualByComparingTo("6500");
    assertThat(summary.totalLiabilities()).isEqualByComparingTo("5800");
    assertThat(summary.netWorth()).isEqualByComparingTo("700");
    assertThat(summary.futureBalance()).isEqualByComparingTo("400");
    assertThat(summary.monthlyIncome()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.monthlyExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.dailySpending()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(summary.lastSyncedAt()).isEqualTo(newestSync);
  }

  @Test
  void computeCalculatesMonthlyIncomeAndExpensesForCurrentMonth() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    LocalDate today = LocalDate.now();
    transactionRepository.save(transaction(appUser.getId(), 1L, today, "Salary", "2000"));
    transactionRepository.save(transaction(appUser.getId(), 2L, today, "Groceries", "-50"));
    transactionRepository.save(
        transaction(appUser.getId(), 3L, today.withDayOfMonth(1), "Rent", "-1000"));
    transactionRepository.save(
        transaction(appUser.getId(), 4L, today.minusMonths(1), "Old rent", "-900"));

    DashboardSummaryResponse summary = dashboardSummaryService.compute(appUser.getEmail());

    assertThat(summary.monthlyIncome()).isEqualByComparingTo("2000");
    assertThat(summary.monthlyExpenses()).isEqualByComparingTo("1050");
  }

  @Test
  void computeCalculatesDailySpendingFromTodaysExpensesOnly() {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    LocalDate today = LocalDate.now();
    transactionRepository.save(transaction(appUser.getId(), 1L, today, "Coffee", "-12"));
    transactionRepository.save(transaction(appUser.getId(), 2L, today, "Lunch", "-45"));
    transactionRepository.save(transaction(appUser.getId(), 3L, today, "Refund", "20"));
    transactionRepository.save(
        transaction(appUser.getId(), 4L, today.minusDays(1), "Groceries", "-100"));

    DashboardSummaryResponse summary = dashboardSummaryService.compute(appUser.getEmail());

    assertThat(summary.dailySpending()).isEqualByComparingTo("57");
  }

  private Account account(
      Long userId,
      Long externalAccountId,
      String type,
      String balance,
      String coming,
      LocalDateTime lastUpdate) {
    return account(userId, externalAccountId, type, balance, coming, lastUpdate, false);
  }

  private Account account(
      Long userId,
      Long externalAccountId,
      String type,
      String balance,
      String coming,
      LocalDateTime lastUpdate,
      boolean disabled) {
    return Account.builder()
        .userId(userId)
        .externalAccountId(externalAccountId)
        .name(type + " account")
        .type(type)
        .balance(new BigDecimal(balance))
        .coming(new BigDecimal(coming))
        .currency("EUR")
        .lastUpdate(lastUpdate)
        .disabled(disabled)
        .build();
  }

  private Transaction transaction(
      Long userId, Long externalTransactionId, LocalDate date, String label, String value) {
    return Transaction.builder()
        .userId(userId)
        .externalTransactionId(externalTransactionId)
        .date(date)
        .label(label)
        .value(new BigDecimal(value))
        .category("OTHER")
        .build();
  }
}
