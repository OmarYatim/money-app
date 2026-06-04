package com.moneyapp.backend.reports.controller;

import com.moneyapp.backend.reports.dto.IncomeExpensesResponse;
import com.moneyapp.backend.reports.dto.NetWorthHistoryResponse;
import com.moneyapp.backend.reports.dto.SpendingByCategoryResponse;
import com.moneyapp.backend.reports.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

  private final ReportService reportService;

  @GetMapping("/spending-by-category")
  public ResponseEntity<List<SpendingByCategoryResponse>> spendingByCategory(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
      @RequestParam(required = false) Long accountId,
      Authentication authentication) {
    return ResponseEntity.ok(
        reportService.spendingByCategory(
            authenticatedEmail(authentication), startDate, endDate, accountId));
  }

  @GetMapping("/income-vs-expenses")
  public ResponseEntity<List<IncomeExpensesResponse>> incomeVsExpenses(
      @RequestParam(defaultValue = "6") int months,
      @RequestParam(required = false) Long accountId,
      Authentication authentication) {
    return ResponseEntity.ok(
        reportService.incomeVsExpenses(authenticatedEmail(authentication), months, accountId));
  }

  @GetMapping("/net-worth-history")
  public ResponseEntity<List<NetWorthHistoryResponse>> netWorthHistory(
      @RequestParam(defaultValue = "3") int months, Authentication authentication) {
    List<NetWorthHistoryResponse> history =
        reportService.netWorthHistory(authenticatedEmail(authentication), months);
    return history.isEmpty() ? ResponseEntity.noContent().build() : ResponseEntity.ok(history);
  }

  private String authenticatedEmail(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return authentication.getName();
  }
}
