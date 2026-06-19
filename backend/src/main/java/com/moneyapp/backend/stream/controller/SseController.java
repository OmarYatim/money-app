package com.moneyapp.backend.stream.controller;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.auth.service.JwtService;
import com.moneyapp.backend.stream.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class SseController {

  private final JwtService jwtService;
  private final CurrentAppUserService currentAppUserService;
  private final SseEmitterService sseEmitterService;

  @GetMapping("/events")
  public ResponseEntity<SseEmitter> events(@RequestParam("access_token") String accessToken) {
    String email = jwtService.extractEmail(accessToken).orElse(null);
    if (email == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    AppUser appUser = currentAppUserService.resolveExisting(email);
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .body(sseEmitterService.register(appUser.getId()));
  }
}
