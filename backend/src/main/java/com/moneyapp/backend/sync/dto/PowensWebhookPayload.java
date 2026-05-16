package com.moneyapp.backend.sync.dto;

import java.util.Map;

public record PowensWebhookPayload(String event, String powensUserId, Long connectionId) {

  public static PowensWebhookPayload from(Map<String, Object> payload) {
    String event = stringValue(payload, "event", "type");
    String powensUserId = stringValue(payload, "user_id", "id_user");
    if (powensUserId == null) {
      powensUserId = stringValue(nestedMap(payload, "data"), "user_id", "id_user");
    }

    Long connectionId = longValue(payload, "connection_id", "id_connection");
    if (connectionId == null) {
      connectionId = longValue(nestedMap(payload, "data"), "connection_id", "id_connection");
    }

    return new PowensWebhookPayload(event, powensUserId, connectionId);
  }

  public boolean isConnectionSynced() {
    return "CONNECTION_SYNCED".equalsIgnoreCase(event);
  }

  private static String stringValue(Map<String, Object> payload, String... keys) {
    if (payload == null) {
      return null;
    }

    for (String key : keys) {
      Object value = payload.get(key);
      if (value != null && !value.toString().isBlank()) {
        return value.toString();
      }
    }

    return null;
  }

  private static Long longValue(Map<String, Object> payload, String... keys) {
    String value = stringValue(payload, keys);
    if (value == null) {
      return null;
    }

    try {
      return Long.valueOf(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> nestedMap(Map<String, Object> payload, String key) {
    Object value = payload == null ? null : payload.get(key);
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }

    return Map.of();
  }
}
