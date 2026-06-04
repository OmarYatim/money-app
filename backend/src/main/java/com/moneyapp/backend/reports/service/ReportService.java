package com.moneyapp.backend.reports.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.reports.dto.IncomeExpensesResponse;
import com.moneyapp.backend.reports.dto.NetWorthHistoryResponse;
import com.moneyapp.backend.reports.dto.SpendingByCategoryResponse;
import com.moneyapp.backend.reports.repository.NetWorthSnapshotRepository;
import com.moneyapp.backend.transaction.entity.Transaction;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ReportService {

  private final CurrentAppUserService currentAppUserService;
  private final AccountRepository accountRepository;
  private final TransactionRepository transactionRepository;
  private final NetWorthSnapshotRepository netWorthSnapshotRepository;

  @Transactional(readOnly = true)
  public List<SpendingByCategoryResponse> spendingByCategory(
      String email, LocalDate startDate, LocalDate endDate, Long accountId) {
    validateDateRange(startDate, endDate);
    AppUser appUser = currentAppUserService.resolveExisting(email);
    validateAccountFilter(appUser.getId(), accountId);

    Map<String, BigDecimal> totals = new LinkedHashMap<>();
    transactionsForRange(appUser.getId(), startDate, endDate, accountId).stream()
        .filter(transaction -> !transaction.isInternalTransfer())
        .filter(transaction -> transaction.getValue().compareTo(BigDecimal.ZERO) < 0)
        .forEach(
            transaction ->
                totals.merge(
                    transaction.getCategory(), transaction.getValue().abs(), BigDecimal::add));

    BigDecimal totalSpend = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    if (totalSpend.compareTo(BigDecimal.ZERO) == 0) {
      return List.of();
    }

    return totals.entrySet().stream()
        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
        .map(
            entry ->
                new SpendingByCategoryResponse(
                    entry.getKey(),
                    entry.getValue(),
                    entry
                        .getValue()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalSpend, 1, RoundingMode.HALF_UP)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<IncomeExpensesResponse> incomeVsExpenses(String email, int months, Long accountId) {
    validateMonths(months);
    AppUser appUser = currentAppUserService.resolveExisting(email);
    validateAccountFilter(appUser.getId(), accountId);

    YearMonth currentMonth = YearMonth.now();
    YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
    LocalDate startDate = firstMonth.atDay(1);
    LocalDate endDate = currentMonth.atEndOfMonth();
    Map<YearMonth, MonthTotals> totalsByMonth = new LinkedHashMap<>();
    for (int i = 0; i < months; i++) {
      totalsByMonth.put(firstMonth.plusMonths(i), new MonthTotals());
    }

    transactionsForRange(appUser.getId(), startDate, endDate, accountId).stream()
        .filter(transaction -> !transaction.isInternalTransfer())
        .forEach(
            transaction -> {
              YearMonth month = YearMonth.from(transaction.getDate());
              MonthTotals totals = totalsByMonth.get(month);
              if (totals != null) {
                totals.add(transaction.getValue());
              }
            });

    return totalsByMonth.entrySet().stream()
        .map(
            entry ->
                new IncomeExpensesResponse(
                    entry.getKey().toString(),
                    entry.getValue().income,
                    entry.getValue().expenses,
                    entry.getValue().income.subtract(entry.getValue().expenses)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<NetWorthHistoryResponse> netWorthHistory(String email, int months) {
    validateMonths(months);
    AppUser appUser = currentAppUserService.resolveExisting(email);
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusMonths(months);

    return netWorthSnapshotRepository
        .findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            appUser.getId(), startDate, endDate)
        .stream()
        .map(
            snapshot ->
                new NetWorthHistoryResponse(snapshot.getSnapshotDate(), snapshot.getNetWorth()))
        .toList();
  }

  private List<Transaction> transactionsForRange(
      Long userId, LocalDate startDate, LocalDate endDate, Long accountId) {
    return transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate).stream()
        .filter(transaction -> accountId == null || accountId.equals(transaction.getAccountId()))
        .toList();
  }

  private void validateDateRange(LocalDate startDate, LocalDate endDate) {
    if (startDate == null || endDate == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "startDate and endDate are required");
    }
    if (startDate.isAfter(endDate)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
    }
  }

  private void validateMonths(int months) {
    if (months < 1 || months > 24) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "months must be between 1 and 24");
    }
  }

  private void validateAccountFilter(Long userId, Long accountId) {
    if (accountId == null) {
      return;
    }
    accountRepository
        .findByIdAndUserId(accountId, userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
  }

  private static final class MonthTotals {
    private BigDecimal income = BigDecimal.ZERO;
    private BigDecimal expenses = BigDecimal.ZERO;

    private void add(BigDecimal value) {
      if (value.compareTo(BigDecimal.ZERO) > 0) {
        income = income.add(value);
      } else if (value.compareTo(BigDecimal.ZERO) < 0) {
        expenses = expenses.add(value.abs());
      }
    }
  }
}
