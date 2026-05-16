package com.moneyapp.backend.sync.controller;

import com.moneyapp.backend.auth.repository.AppUserRepository;
import com.moneyapp.backend.sync.dto.PowensWebhookPayload;
import com.moneyapp.backend.sync.enums.SyncEventTrigger;
import com.moneyapp.backend.sync.service.AsyncDataSyncService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhooks/powens")
@RequiredArgsConstructor
public class PowensWebhookController {

  private final AppUserRepository appUserRepository;
  private final AsyncDataSyncService asyncDataSyncService;

  @PostMapping
  public ResponseEntity<Void> handleWebhook(@RequestBody Map<String, Object> payload) {
    PowensWebhookPayload webhookPayload = PowensWebhookPayload.from(payload);
    if (!webhookPayload.isConnectionSynced()) {
      return ResponseEntity.ok().build();
    }

    if (webhookPayload.powensUserId() != null) {
      appUserRepository
          .findByPowensUserId(webhookPayload.powensUserId())
          .ifPresent(
              appUser ->
                  asyncDataSyncService.syncAsync(
                      appUser, SyncEventTrigger.WEBHOOK, webhookPayload.connectionId()));
    }

    return ResponseEntity.ok().build();
  }
}
