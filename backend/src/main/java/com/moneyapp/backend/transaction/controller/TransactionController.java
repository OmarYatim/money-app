package com.moneyapp.backend.transaction.controller;

import com.moneyapp.backend.transaction.dto.TransactionResponse;
import com.moneyapp.backend.transaction.dto.UpdateTransactionCategoryRequest;
import com.moneyapp.backend.transaction.dto.UpdateTransactionInternalTransferRequest;
import com.moneyapp.backend.transaction.service.TransactionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

  private final TransactionService transactionService;

  @GetMapping
  public ResponseEntity<List<TransactionResponse>> getTransactions(Authentication authentication) {
    return ResponseEntity.ok(
        transactionService.findTransactions(authenticatedEmail(authentication)));
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
