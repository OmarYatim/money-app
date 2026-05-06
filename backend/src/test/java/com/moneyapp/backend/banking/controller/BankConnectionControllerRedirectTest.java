package com.moneyapp.backend.banking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneyapp.backend.banking.dto.BankConnectionCallbackResponse;
import com.moneyapp.backend.banking.service.BankConnectionService;
import com.moneyapp.backend.config.AppProperties;
import com.moneyapp.backend.config.AppProperties.JwtProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BankConnectionControllerRedirectTest {

  private static final AppProperties APP_PROPERTIES =
      new AppProperties(
          "https://app.example",
          new JwtProperties("test-secret-key-must-be-at-least-32-chars!!", 900000));

  private final StubBankConnectionService bankConnectionService = new StubBankConnectionService();
  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(
              new BankConnectionController(bankConnectionService, APP_PROPERTIES))
          .build();

  @Test
  void callbackRedirectsToAccountsAfterSuccessfulConnection() throws Exception {
    bankConnectionService.response =
        BankConnectionCallbackResponse.builder()
            .status("connected")
            .message("Bank connection completed.")
            .connectionIds(List.of(12L, 13L))
            .build();

    mockMvc
        .perform(
            get("/api/bank/callback").param("connection_ids", "12,13").param("state", "csrf-state"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://app.example/accounts?connected=true"));

    assertThat(bankConnectionService.connectionIds).isEqualTo("12,13");
    assertThat(bankConnectionService.error).isNull();
    assertThat(bankConnectionService.state).isEqualTo("csrf-state");
  }

  @Test
  void callbackRedirectsToAccountsAfterCancelledConnection() throws Exception {
    bankConnectionService.response =
        BankConnectionCallbackResponse.builder()
            .status("cancelled")
            .message("Bank connection cancelled.")
            .connectionIds(List.of())
            .build();

    mockMvc
        .perform(
            get("/api/bank/callback").param("error", "access_denied").param("state", "csrf-state"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://app.example/accounts?connected=false"));

    assertThat(bankConnectionService.connectionIds).isNull();
    assertThat(bankConnectionService.error).isEqualTo("access_denied");
    assertThat(bankConnectionService.state).isEqualTo("csrf-state");
  }

  private static final class StubBankConnectionService extends BankConnectionService {

    private String connectionIds;
    private String error;
    private String state;
    private BankConnectionCallbackResponse response;

    private StubBankConnectionService() {
      super(null, null, null, null, null, null, null, null);
    }

    @Override
    public BankConnectionCallbackResponse handleCallback(
        String connectionIds, String error, String state) {
      this.connectionIds = connectionIds;
      this.error = error;
      this.state = state;
      return response;
    }
  }
}
