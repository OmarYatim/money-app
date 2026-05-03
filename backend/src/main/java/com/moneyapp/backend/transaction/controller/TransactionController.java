package com.moneyapp.backend.transaction.controller;

import com.moneyapp.backend.transaction.dto.TransactionFilter;
import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.dto.UpdateTransactionCategoryRequest;
import com.moneyapp.backend.transaction.dto.UpdateTransactionInternalTransferRequest;
import com.moneyapp.backend.transaction.service.TransactionService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  @GetMapping
  public ResponseEntity<Page<TransactionResponse>> getTransactions(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Long accountId,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate minDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate maxDate,
      @RequestParam(required = false) BigDecimal minAmount,
      @RequestParam(required = false) BigDecimal maxAmount,
      @RequestParam(required = false) String keyword,
      Authentication authentication) {
    PageRequest pageable =
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id")));
    return ResponseEntity.ok(
        transactionService.findTransactions(
            authenticatedEmail(authentication),
            new TransactionFilter(
                accountId, category, minDate, maxDate, minAmount, maxAmount, keyword),
            pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<TransactionResponse> getTransaction(
      @PathVariable Long id, Authentication authentication) {
    return ResponseEntity.ok(
        transactionService.getTransaction(authenticatedEmail(authentication), id));
  }

  @PatchMapping("/{id}/category")
  public ResponseEntity<TransactionResponse> updateCategory(
      @PathVariable Long id,
      @Valid @RequestBody UpdateTransactionCategoryRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        transactionService.updateCategory(
            authenticatedEmail(authentication), id, request.category()));
  }

  @PatchMapping("/{id}/internal-transfer")
  public ResponseEntity<TransactionResponse> updateInternalTransfer(
      @PathVariable Long id,
      @RequestBody UpdateTransactionInternalTransferRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        transactionService.updateInternalTransfer(
            authenticatedEmail(authentication), id, request.internalTransfer()));
  }

  private String authenticatedEmail(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return authentication.getName();
  }
}
