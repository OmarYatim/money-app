package com.moneyapp.backend.banking.controller;

import com.moneyapp.backend.banking.dto.BankConnectResponse;
import com.moneyapp.backend.banking.dto.BankConnectionCallbackResponse;
import com.moneyapp.backend.banking.service.BankConnectionService;
import com.moneyapp.backend.config.AppProperties;
import java.net.URI;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/bank")
@RequiredArgsConstructor
public class BankConnectionController {

  private final BankConnectionService bankConnectionService;
  private final AppProperties appProperties;

  @GetMapping("/connect")
  public ResponseEntity<BankConnectResponse> connect(Authentication authentication) {
    requireAuthenticated(authentication);

    return ResponseEntity.ok(bankConnectionService.createConnectLink(authentication.getName()));
  }

  @DeleteMapping("/connections/{connectionId}")
  public ResponseEntity<Void> disconnect(
      @PathVariable Long connectionId,
      @RequestParam boolean deleteData,
      Authentication authentication) {
    requireAuthenticated(authentication);

    bankConnectionService.disconnectConnection(authentication.getName(), connectionId, deleteData);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/callback")
  public ResponseEntity<Void> callback(
      @RequestParam(required = false, name = "connection_ids") String connectionIds,
      @RequestParam(required = false, name = "connection_id") String connectionId,
      @RequestParam(required = false) String error,
      @RequestParam(required = false) String state) {
    String mergedIds =
        Stream.of(connectionIds, connectionId)
            .filter(value -> value != null && !value.isBlank())
            .collect(Collectors.joining(","));

    BankConnectionCallbackResponse response =
        bankConnectionService.handleCallback(mergedIds.isEmpty() ? null : mergedIds, error, state);

    return ResponseEntity.status(HttpStatus.FOUND).location(frontendRedirectUri(response)).build();
  }

  private URI frontendRedirectUri(BankConnectionCallbackResponse response) {
    return UriComponentsBuilder.fromUriString(appProperties.frontendUrl())
        .path("/accounts")
        .queryParam("connected", "connected".equals(response.status()))
        .build()
        .toUri();
  }

  private void requireAuthenticated(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
  }
}
