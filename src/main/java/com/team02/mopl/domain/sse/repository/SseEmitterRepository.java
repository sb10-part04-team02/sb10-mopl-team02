package com.team02.mopl.domain.sse.repository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Repository
public class SseEmitterRepository {
  private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

  public Optional<SseEmitter> findByUserId(UUID userId) {
    return Optional.ofNullable(emitters.get(userId));
  }

  public Optional<SseEmitter> save(UUID userId, SseEmitter sseEmitter) {
    return Optional.ofNullable(emitters.put(userId, sseEmitter));
  }

  public void delete(UUID userId, SseEmitter emitter) {
    emitters.remove(userId, emitter);
  }
}
