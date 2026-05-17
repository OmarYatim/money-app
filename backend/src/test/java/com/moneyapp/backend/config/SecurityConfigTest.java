package com.moneyapp.backend.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
      "app.jwt.expiration-ms=900000"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

  private static final String POWENS_WEBHOOK_AUTHORIZATION = "Bearer test-webhook-token";

  @Autowired private MockMvc mockMvc;

  @Test
  void healthEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void powensWebhookEndpointRequiresBearerToken() throws Exception {
    mockMvc
        .perform(post("/webhooks/powens").contentType("application/json").content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void powensWebhookEndpointRejectsWrongBearerToken() throws Exception {
    mockMvc
        .perform(
            post("/webhooks/powens")
                .header("Authorization", "Bearer wrong-token")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void powensWebhookEndpointAcceptsValidBearerToken() throws Exception {
    mockMvc
        .perform(
            post("/webhooks/powens")
                .header("Authorization", POWENS_WEBHOOK_AUTHORIZATION)
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
  }

  @Test
  void powensWebhookEndpointAcceptsMissingContentType() throws Exception {
    mockMvc
        .perform(
            post("/webhooks/powens")
                .header("Authorization", POWENS_WEBHOOK_AUTHORIZATION)
                .content(
                    "{\"event\":\"CONNECTION_SYNCED\",\"user_id\":\"debug\",\"connection_id\":123}"))
        .andExpect(status().isOk());
  }

  @Test
  void powensWebhookEndpointIgnoresPowensOriginHeader() throws Exception {
    mockMvc
        .perform(
            post("/webhooks/powens")
                .header("Authorization", POWENS_WEBHOOK_AUTHORIZATION)
                .header("Origin", "poc4oy-sandbox.biapi.pro")
                .contentType("application/json")
                .content(
                    "{\"event\":\"CONNECTION_SYNCED\",\"user_id\":\"debug\",\"connection_id\":123}"))
        .andExpect(status().isOk());
  }

  @Test
  void corsAllowsConfiguredFrontendOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/auth/register")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
  }
}
