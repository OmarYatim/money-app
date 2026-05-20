package com.moneyapp.backend.sync.controller;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.sync.dto.PowensWebhookPayload;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.service.AsyncDataSyncService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/webhooks/powens")
@RequiredArgsConstructor
@Slf4j
public class PowensWebhookController {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final TypeReference<Map<String, Object>> WEBHOOK_PAYLOAD_TYPE =
      new TypeReference<>() {};

  private final AppUserRepository appUserRepository;
  private final AsyncDataSyncService asyncDataSyncService;
  private final JsonMapper jsonMapper;

  @PostMapping(consumes = MediaType.ALL_VALUE)
  public ResponseEntity<Void> handleWebhook(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody(required = false) String rawBody) {
    if (!hasBearerToken(authorization)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    if (rawBody == null || rawBody.isBlank()) {
      log.warn("Received empty Powens webhook payload");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    Map<String, Object> payload = parsePayload(rawBody);
    if (payload.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    PowensWebhookPayload webhookPayload = PowensWebhookPayload.from(payload);
    Optional<AppUser> appUser = findAppUser(webhookPayload);
    if (appUser.isEmpty()) {
      log.warn("No app user found for Powens webhook userId={}", webhookPayload.powensUserId());
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    if (!isAuthorized(authorization, appUser.get())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    if (!webhookPayload.isConnectionSynced()) {
      log.warn("Unhandled Powens event type: {}", webhookPayload.event());
      return ResponseEntity.ok().build();
    }

    asyncDataSyncService.syncAsync(
        appUser.get(), SyncEventTrigger.WEBHOOK, webhookPayload.connectionId());

    return ResponseEntity.ok().build();
  }

  private Map<String, Object> parsePayload(String rawBody) {
    try {
      return jsonMapper.readValue(rawBody, WEBHOOK_PAYLOAD_TYPE);
    } catch (JacksonException exception) {
      log.warn("Received invalid Powens webhook payload", exception);
      return Map.of();
    }
  }

  private Optional<AppUser> findAppUser(PowensWebhookPayload webhookPayload) {
    return webhookPayload.powensUserId() == null
        ? Optional.empty()
        : appUserRepository.findByPowensUserId(webhookPayload.powensUserId());
  }

  private boolean isAuthorized(String authorization, AppUser appUser) {
    String token = authorization.substring(BEARER_PREFIX.length());
    return appUser.getPowensToken() != null
        && MessageDigest.isEqual(secretBytes(token), secretBytes(appUser.getPowensToken()));
  }

  private boolean hasBearerToken(String authorization) {
    return authorization != null && authorization.startsWith(BEARER_PREFIX);
  }

  private byte[] secretBytes(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
