package com.moneyapp.backend.banking.service;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.banking.dto.BankConnectResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankConnectionService {

  private final PowensAuthService powensAuthService;
  private final PowensWebviewService powensWebviewService;

  public BankConnectResponse createConnectLink(String email) {
    AppUser appUser = powensAuthService.ensurePowensUser(email);
    String temporaryCode = powensAuthService.createTemporaryWebviewCode(appUser);
    String state = UUID.randomUUID().toString();

    return BankConnectResponse.builder()
        .webviewUrl(powensWebviewService.buildConnectUrl(temporaryCode, state))
        .state(state)
        .build();
  }
}
