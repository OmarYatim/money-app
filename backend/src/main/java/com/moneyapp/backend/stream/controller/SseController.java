package com.moneyapp.backend.stream.controller;

import com.moneyapp.backend.auth.entity.AppUser;
import com.moneyapp.backend.auth.service.CurrentAppUserService;
import com.moneyapp.backend.auth.service.JwtService;
import com.moneyapp.backend.stream.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class SseController {

  private final JwtService jwtService;
  private final CurrentAppUserService currentAppUserService;
  private final SseEmitterService sseEmitterService;

  @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(@RequestParam("access_token") String accessToken) {
    String email =
        jwtService
            .extractEmail(accessToken)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    AppUser appUser = currentAppUserService.resolveExisting(email);
    return sseEmitterService.register(appUser.getId());
  }
}
