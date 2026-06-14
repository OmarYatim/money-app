package com.moneyapp.backend.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
      "app.jwt.expiration-ms=900000",
      "app.rate-limit.enabled=true",
      "app.rate-limit.login.capacity=10",
      "app.rate-limit.login.refill-seconds=900",
      "app.rate-limit.refresh.capacity=20",
      "app.rate-limit.refresh.refill-seconds=60",
      "app.rate-limit.login-failure.threshold=100000",
      "app.rate-limit.login-failure.delay-seconds=0"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitFilterTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void loginReturnsTooManyRequestsAfterTenAttemptsFromSameIp() throws Exception {
    String body = "{\"email\":\"limit@example.com\",\"password\":\"wrong\"}";

    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(
              post("/api/auth/login")
                  .header("CF-Connecting-IP", "203.0.113.20")
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            post("/api/auth/login")
                .header("CF-Connecting-IP", "203.0.113.20")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));
  }

  @Test
  void loginRateLimitIsPerIpAddress() throws Exception {
    String body = "{\"email\":\"limit@example.com\",\"password\":\"wrong\"}";

    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(
              post("/api/auth/login")
                  .header("CF-Connecting-IP", "203.0.113.10")
                  .contentType("application/json")
                  .content(body))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(
            post("/api/auth/login")
                .header("CF-Connecting-IP", "203.0.113.11")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshReturnsTooManyRequestsAfterTwentyAttemptsFromSameIp() throws Exception {
    for (int i = 0; i < 20; i++) {
      mockMvc
          .perform(post("/api/auth/refresh").header("CF-Connecting-IP", "203.0.113.30"))
          .andExpect(status().isUnauthorized());
    }

    mockMvc
        .perform(post("/api/auth/refresh").header("CF-Connecting-IP", "203.0.113.30"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
  }
}
