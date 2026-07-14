package com.team02.mopl.domain.sse.service;

import com.team02.mopl.domain.sse.repository.SseEmitterRepository;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEventService {

  private final SseEmitterRepository sseEmitterRepository;

  // 사용자 ID 기준으로 연결된 emitter를 찾아 SSE 이벤트를 전송
  public void send(UUID receiverId, String eventName, String eventId, Object data) {
    sseEmitterRepository
        .findByUserId(receiverId)
        .ifPresent(emitter -> sendEvent(receiverId, emitter, eventName, eventId, data));
  }

  // 실제 SSE 이벤트를 구성해 전송하고, 실패하면 해당 emitter를 제거
  private void sendEvent(
      UUID receiverId, SseEmitter emitter, String eventName, String eventId, Object data) {
    try {
      emitter.send(
          SseEmitter.event()
              .id(SseEventId.fromEventName(eventName, eventId))
              .name(eventName)
              .data(data));
    } catch (IOException e) {
      sseEmitterRepository.delete(receiverId, emitter);
      log.warn("Failed to send SSE event. receiverId={}, eventName={}", receiverId, eventName, e);
    }
  }
}
