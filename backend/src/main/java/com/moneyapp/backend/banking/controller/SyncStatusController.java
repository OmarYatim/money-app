package com.moneyapp.backend.banking.controller;

import com.moneyapp.backend.banking.dto.SyncStatusResponse;
import com.moneyapp.backend.banking.service.ConnectionStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncStatusController {

  private final ConnectionStatusService connectionStatusService;

  @GetMapping("/status")
  public ResponseEntity<SyncStatusResponse> getStatus(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return ResponseEntity.ok(connectionStatusService.getStatus(authentication.getName()));
  }

  @PostMapping
  public ResponseEntity<SyncStatusResponse> sync(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }

    return ResponseEntity.ok(connectionStatusService.syncNow(authentication.getName()));
  }
}
