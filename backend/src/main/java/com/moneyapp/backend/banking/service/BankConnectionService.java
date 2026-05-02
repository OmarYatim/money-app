package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.banking.dto.BankConnectResponse;
import com.moneyapp.backend.banking.dto.BankConnectionCallbackResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankConnectionService {

  private final PowensAuthService powensAuthService;
  private final BankConnectionStateService bankConnectionStateService;
  private final UserConnectionService userConnectionService;
  private final AccountService accountService;
  private final PowensWebviewService powensWebviewService;

  public BankConnectResponse createConnectLink(String email) {
    AppUser appUser = powensAuthService.ensurePowensUser(email);
    String temporaryCode = powensAuthService.createTemporaryWebviewCode(appUser);
    String state = UUID.randomUUID().toString();
    bankConnectionStateService.create(appUser.getId(), state);

    return BankConnectResponse.builder()
        .webviewUrl(powensWebviewService.buildConnectUrl(temporaryCode, state))
        .state(state)
        .build();
  }

  public BankConnectionCallbackResponse handleCallback(
      String email, String connectionIds, String error, String state) {
    AppUser appUser = powensAuthService.ensurePowensUser(email);
    bankConnectionStateService.consume(appUser.getId(), state);

    if (error != null && !error.isBlank()) {
      return BankConnectionCallbackResponse.builder()
          .status("cancelled")
          .message("Bank connection cancelled.")
          .connectionIds(List.of())
          .build();
    }

    List<Long> parsedConnectionIds = parseConnectionIds(connectionIds);
    userConnectionService.upsertActiveConnections(appUser.getId(), parsedConnectionIds);
    accountService.syncAccounts(appUser);

    return BankConnectionCallbackResponse.builder()
        .status("connected")
        .message("Bank connection completed.")
        .connectionIds(parsedConnectionIds)
        .build();
  }

  private List<Long> parseConnectionIds(String connectionIds) {
    if (connectionIds == null || connectionIds.isBlank()) {
      return List.of();
    }

    return Arrays.stream(connectionIds.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .map(Long::parseLong)
        .toList();
  }
}
