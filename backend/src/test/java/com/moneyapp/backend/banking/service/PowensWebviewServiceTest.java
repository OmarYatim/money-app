package com.moneyapp.backend.banking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moneyapp.backend.config.PowensProperties;
import org.junit.jupiter.api.Test;

class PowensWebviewServiceTest {

  @Test
  void buildConnectUrlIncludesRequiredWebviewParameters() {
    PowensWebviewService service =
        new PowensWebviewService(
            new PowensProperties(
                "sandbox.powens.test",
                "client-123",
                "secret",
                "manage-token",
                "https://local.moneyapp.me/api/bank/callback"));

    String url = service.buildConnectUrl("temporary-code", "csrf-state");

    assertThat(url).startsWith("https://sandbox.powens.test/auth/webview/connect?");
    assertThat(url).contains("client_id=client-123");
    assertThat(url)
        .contains("redirect_uri=https%3A%2F%2Flocal.moneyapp.me%2Fapi%2Fbank%2Fcallback");
    assertThat(url).contains("code=temporary-code");
    assertThat(url).contains("state=csrf-state");
  }
}
