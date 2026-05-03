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

  private final CurrentAppUserService currentAppUserService;
  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;

  @Transactional(readOnly = true)
  public DashboardSummaryResponse compute(String email) {
    AppUser appUser = currentAppUserService.resolveExisting(email);
    List<Account> accounts =
        accountRepository.findByUserIdAndDisabledFalseOrderByNameAsc(appUser.getId());
    List<Transaction> monthlyTransactions = findCurrentMonthTransactions(appUser.getId());

    BigDecimal totalAssets = sumAssets(accounts);
    BigDecimal totalLiabilities = sumLiabilities(accounts);

    return new DashboardSummaryResponse(
        totalAssets.subtract(totalLiabilities),
        totalAssets,
        totalLiabilities,
        sumFutureBalance(accounts),
        sumMonthlyIncome(monthlyTransactions),
        sumMonthlyExpenses(monthlyTransactions),
        BigDecimal.ZERO,
        lastSyncedAt(accounts));
  }

  private List<Transaction> findCurrentMonthTransactions(Long userId) {
    LocalDate today = LocalDate.now();
    return transactionRepository.findByUserIdAndDateBetween(userId, today.withDayOfMonth(1), today);
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

  private BigDecimal sumFutureBalance(List<Account> accounts) {
    return accounts.stream()
        .map(account -> account.getBalance().add(account.getComing()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumMonthlyIncome(List<Transaction> transactions) {
    return transactions.stream()
        .map(Transaction::getValue)
        .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal sumMonthlyExpenses(List<Transaction> transactions) {
    return transactions.stream()
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
