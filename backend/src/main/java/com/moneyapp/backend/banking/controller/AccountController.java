package com.moneyapp.backend.banking.controller;

import com.moneyapp.backend.banking.dto.AccountResponse;
import com.moneyapp.backend.banking.dto.UpdateAccountRequest;
import com.moneyapp.backend.banking.service.AccountService;
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
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

  private final AccountService accountService;

  @GetMapping
  public ResponseEntity<List<AccountResponse>> getAccounts(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return ResponseEntity.ok(accountService.findAccounts(authentication.getName()));
  }

  @GetMapping("/transaction-filter-options")
  public ResponseEntity<List<AccountResponse>> getTransactionFilterAccounts(
      Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return ResponseEntity.ok(
        accountService.findTransactionFilterAccounts(authentication.getName()));
  }

  @PatchMapping("/{accountId}")
  public ResponseEntity<AccountResponse> updateAccount(
      Authentication authentication,
      @PathVariable Long accountId,
      @Valid @RequestBody UpdateAccountRequest request) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return ResponseEntity.ok(
        accountService.updateAccount(authentication.getName(), accountId, request.name()));
  }
}
