package com.moneyapp.backend.sync.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PowensWebhookPayloadTest {

  @Test
  void parsesConnectionSyncedPayloadWithNestedPowensAliases() {
    PowensWebhookPayload payload =
        PowensWebhookPayload.from(
            Map.of(
                "event",
                "CONNECTION_SYNCED",
                "data",
                Map.of("id_user", "powens-user-1", "id_connection", 15)));

    assertThat(payload.isConnectionSynced()).isTrue();
    assertThat(payload.powensUserId()).isEqualTo("powens-user-1");
    assertThat(payload.connectionId()).isEqualTo(15L);
  }

  @Test
  void parsesTopLevelWebhookPayload() {
    PowensWebhookPayload payload =
        PowensWebhookPayload.from(
            Map.of("type", "CONNECTION_SYNCED", "user_id", "powens-user-2", "connection_id", "16"));

    assertThat(payload.isConnectionSynced()).isTrue();
    assertThat(payload.powensUserId()).isEqualTo("powens-user-2");
    assertThat(payload.connectionId()).isEqualTo(16L);
  }
}
