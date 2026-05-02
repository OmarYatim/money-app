package com.moneyapp.backend.banking.controller;

import com.moneyapp.backend.banking.dto.BankConnectResponse;
import com.moneyapp.backend.banking.service.BankConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankConnectionController {

  private final BankConnectionService bankConnectionService;

  @GetMapping("/connect")
  public ResponseEntity<BankConnectResponse> connect(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return ResponseEntity.ok(bankConnectionService.createConnectLink(authentication.getName()));
  }
}
