package com.moneyapp.backend.stream.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterServiceTest {

  @Test
  void registersEmitterForUser() {
    SseEmitterService service = new SseEmitterService();

    SseEmitter emitter = service.register(42L);
    assertThat(emitter).isNotNull();
    assertThat(service.activeEmitterCount(42L)).isEqualTo(1);
  }
}
