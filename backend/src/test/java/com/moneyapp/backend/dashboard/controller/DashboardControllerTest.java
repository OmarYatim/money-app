package com.moneyapp.backend.dashboard.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.auth.service.JwtService;
import com.moneyapp.backend.banking.entity.Account;
import com.moneyapp.backend.banking.repository.AccountRepository;
import com.moneyapp.backend.transaction.repository.TransactionRepository;
import java.math.BigDecimal;
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
class DashboardControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private JwtService jwtService;

  @Autowired private AppUserRepository appUserRepository;

  @Autowired private AccountRepository accountRepository;

  @Autowired private TransactionRepository transactionRepository;

  @BeforeEach
  void setUp() {
    transactionRepository.deleteAll();
    accountRepository.deleteAll();
    appUserRepository.deleteAll();
  }

  @Test
  void getSummaryRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isUnauthorized());
  }

  @Test
  void getSummaryReturnsAuthenticatedUsersSummary() throws Exception {
    AppUser appUser = appUserRepository.save(AppUser.builder().email("person@example.com").build());
    accountRepository.save(
        Account.builder()
            .userId(appUser.getId())
            .externalAccountId(1L)
            .name("Checking")
            .type("checking")
            .balance(BigDecimal.valueOf(1500))
            .coming(BigDecimal.ZERO)
            .currency("EUR")
            .build());

    mockMvc
        .perform(
            get("/api/dashboard/summary")
                .header("Authorization", "Bearer " + jwtService.generateToken(appUser.getEmail())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAssets").value(1500))
        .andExpect(jsonPath("$.netWorth").value(1500))
        .andExpect(jsonPath("$.monthlyIncome").value(0))
        .andExpect(jsonPath("$.dailySpending").value(0));
  }
}
