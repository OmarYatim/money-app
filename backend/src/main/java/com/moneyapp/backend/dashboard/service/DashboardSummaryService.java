package com.moneyapp.backend.dashboard.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.dashboard.dto.DashboardSummaryResponse;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardSummaryService {

  private static final Set<String> ASSET_TYPES = Set.of("checking", "savings");
  private static final Set<String> LIABILITY_TYPES = Set.of("credit", "loan");
  private static final int EXPENSE_LOOKBACK_DAYS = 30;
  private static final int FUTURE_BALANCE_LOOKAHEAD_DAYS = 30;

  private final CurrentAppUserService currentAppUserService;
  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;

  @Transactional(readOnly = true)
  public DashboardSummaryResponse compute(String email) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    List<Account> accounts =
        accountRepository.findByUserIdAndDisabledFalseOrderByNameAsc(appUser.getId());
    LocalDate today = LocalDate.now();
    List<Transaction> monthlyTransactions = findRecentExpenseWindowTransactions(appUser.getId());
    List<Transaction> dailyTransactions =
        transactionRepository.findByUserIdAndDate(appUser.getId(), today);
    List<Transaction> futureTransactions = findUpcomingTransactions(appUser.getId(), today);

    BigDecimal totalAssets = sumAssets(accounts);
    BigDecimal totalLiabilities = sumLiabilities(accounts);

    return new DashboardSummaryResponse(
        totalAssets.subtract(totalLiabilities),
        totalAssets,
        totalLiabilities,
        sumFutureBalance(totalAssets.subtract(totalLiabilities), futureTransactions),
        sumMonthlyIncome(monthlyTransactions),
        sumMonthlyExpenses(monthlyTransactions),
        sumDailySpending(dailyTransactions),
        lastSyncedAt(accounts));
  }

  @Transactional(readOnly = true)
  public BigDecimal computeNetWorth(Long userId) {
    List<Account> accounts = accountRepository.findByUserIdAndDisabledFalseOrderByNameAsc(userId);
    return sumAssets(accounts).subtract(sumLiabilities(accounts));
  }

  private List<Transaction> findRecentExpenseWindowTransactions(Long userId) {
    LocalDate today = LocalDate.now();
    return transactionRepository.findByUserIdAndDateBetween(
        userId, today.minusDays(EXPENSE_LOOKBACK_DAYS - 1L), today);
  }

  private List<Transaction> findUpcomingTransactions(Long userId, LocalDate today) {
    return transactionRepository.findByUserIdAndDateBetween(
        userId, today, today.plusDays(FUTURE_BALANCE_LOOKAHEAD_DAYS - 1L));
  }

  private BigDecimal sumAssets(List<Account> accounts) {
    return accounts.stream()
        .filter(account -> ASSET_TYPES.contains(normalizedType(account)))
        .map(Account::getBalance)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumLiabilities(List<Account> accounts) {
    return accounts.stream()
        .filter(account -> LIABILITY_TYPES.contains(normalizedType(account)))
        .map(Account::getBalance)
        .map(BigDecimal::abs)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumFutureBalance(BigDecimal currentBalance, List<Transaction> transactions) {
    BigDecimal upcomingNet =
        transactions.stream()
            .filter(t -> !t.isInternalTransfer())
            .map(Transaction::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return currentBalance.add(upcomingNet);
  }

  private BigDecimal sumMonthlyIncome(List<Transaction> transactions) {
    return transactions.stream()
        .filter(t -> !t.isInternalTransfer())
        .map(Transaction::getValue)
        .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumMonthlyExpenses(List<Transaction> transactions) {
    return transactions.stream()
        .filter(t -> !t.isInternalTransfer())
        .map(Transaction::getValue)
        .filter(value -> value.compareTo(BigDecimal.ZERO) < 0)
        .map(BigDecimal::abs)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumDailySpending(List<Transaction> transactions) {
    return transactions.stream()
        .filter(t -> !t.isInternalTransfer())
        .map(Transaction::getValue)
        .filter(value -> value.compareTo(BigDecimal.ZERO) < 0)
        .map(BigDecimal::abs)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private LocalDateTime lastSyncedAt(List<Account> accounts) {
    return accounts.stream()
        .map(Account::getLastUpdate)
        .filter(lastUpdate -> lastUpdate != null)
        .max(LocalDateTime::compareTo)
        .orElse(null);
  }

  private String normalizedType(Account account) {
    return account.getType() == null ? "" : account.getType().toLowerCase();
  }
}
