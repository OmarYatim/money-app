package com.moneyapp.backend.banking.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneyapp.backend.banking.repository.BankConnectionStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "powens.domain=powens.test",
      "powens.client-id=test-client-id",
      "powens.client-secret=test-client-secret",
      "powens.manage-token=test-manage-token",
      "powens.redirect-url=https://local.nexioo.me/api/bank/callback",
      "app.jwt.secret=test-secret-key-must-be-at-least-32-chars!!",
      "app.jwt.expiration-ms=900000"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankConnectionControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private BankConnectionStateRepository bankConnectionStateRepository;

  @BeforeEach
  void setUp() {
    bankConnectionStateRepository.deleteAll();
  }

  @Test
  void connectRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/bank/connect")).andExpect(status().isUnauthorized());
  }

  @Test
  void callbackDoesNotRequireAuthentication() throws Exception {
    mockMvc
        .perform(get("/api/bank/callback").param("state", "missing-state"))
        .andExpect(status().isBadRequest());
  }
}
