package com.moneyapp.backend.stream.service;

import com.moneyapp.backend.stream.StreamEventType;
import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Slf4j
public class SseEmitterService {

  private static final long STREAM_TIMEOUT_MS = 30L * 60L * 1000L;

  private final ConcurrentHashMap<Long, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

  public SseEmitter register(Long userId) {
    SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
    Set<SseEmitter> emitters =
        emittersByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
    emitters.add(emitter);
    log.info("SSE stream registered for userId={} activeEmitters={}", userId, emitters.size());

    emitter.onCompletion(
        () -> {
          removeEmitter(userId, emitter);
          log.info(
              "SSE stream completed for userId={} activeEmitters={}",
              userId,
              activeEmitterCount(userId));
        });
    emitter.onTimeout(
        () -> {
          removeEmitter(userId, emitter);
          log.info(
              "SSE stream timed out for userId={} activeEmitters={}",
              userId,
              activeEmitterCount(userId));
          emitter.complete();
        });
    emitter.onError(
        error -> {
          removeEmitter(userId, emitter);
          log.warn(
              "SSE stream failed for userId={} activeEmitters={}",
              userId,
              activeEmitterCount(userId));
        });

    sendOrRemove(userId, emitter, StreamEventType.CONNECTED, "connected");
    return emitter;
  }

  public void emitDataUpdated(Long userId) {
    emit(userId, StreamEventType.ACCOUNTS_UPDATED);
    emit(userId, StreamEventType.TRANSACTIONS_UPDATED);
  }

  void emit(Long userId, StreamEventType eventType) {
    Set<SseEmitter> emitters = emittersByUser.get(userId);
    if (emitters == null || emitters.isEmpty()) {
      log.info("SSE event={} skipped for userId={} activeEmitters=0", eventType, userId);
      return;
    }

    log.info(
        "SSE event={} emitting for userId={} activeEmitters={}",
        eventType,
        userId,
        emitters.size());
    emitters.forEach(emitter -> sendOrRemove(userId, emitter, eventType, Instant.now().toString()));
  }

  int activeEmitterCount(Long userId) {
    return emittersByUser.getOrDefault(userId, Set.of()).size();
  }

  private void sendOrRemove(
      Long userId, SseEmitter emitter, StreamEventType eventType, String payload) {
    try {
      emitter.send(SseEmitter.event().name(eventType.name()).data(payload));
    } catch (IOException | IllegalStateException exception) {
      log.warn("Removing stale SSE emitter for userId={}", userId);
      removeEmitter(userId, emitter);
    }
  }

  private void removeEmitter(Long userId, SseEmitter emitter) {
    Set<SseEmitter> emitters = emittersByUser.get(userId);
    if (emitters == null) {
      return;
    }

    emitters.remove(emitter);
    if (emitters.isEmpty()) {
      emittersByUser.remove(userId, emitters);
    }
  }
}
