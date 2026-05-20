package com.moneyapp.backend.sync.dto;

import java.util.Map;

public record PowensWebhookPayload(String event, String powensUserId, Long connectionId) {

  private static final String CONNECTION_SYNCED = "CONNECTION_SYNCED";

  public static PowensWebhookPayload from(Map<String, Object> payload) {
    Map<String, Object> data = nestedMap(payload, "data");
    Map<String, Object> user = nestedMap(payload, "user");
    Map<String, Object> connection = nestedMap(payload, "connection");

    String event = event(payload, connection);
    String powensUserId = stringValue(payload, "user_id", "id_user");
    if (powensUserId == null) {
      powensUserId = stringValue(data, "user_id", "id_user");
    }
    if (powensUserId == null) {
      powensUserId = stringValue(user, "id", "user_id", "id_user");
    }
    if (powensUserId == null) {
      powensUserId = stringValue(connection, "id_user", "user_id");
    }

    Long connectionId = longValue(payload, "connection_id", "id_connection");
    if (connectionId == null) {
      connectionId = longValue(data, "connection_id", "id_connection");
    }
    if (connectionId == null) {
      connectionId = longValue(connection, "id", "connection_id", "id_connection");
    }

    return new PowensWebhookPayload(event, powensUserId, connectionId);
  }

  public boolean isConnectionSynced() {
    return CONNECTION_SYNCED.equalsIgnoreCase(event);
  }

  private static String event(Map<String, Object> payload, Map<String, Object> connection) {
    String event = stringValue(payload, "event", "type");
    if (event != null) {
      return event;
    }

    return connection.isEmpty() ? null : CONNECTION_SYNCED;
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
