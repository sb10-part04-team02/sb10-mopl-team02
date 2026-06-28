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
public class SseEmitterService {

  private static final long DEFAULT_TIMEOUT_MILLIS = 30L * 60L * 1000L;
  private static final String CONNECT_EVENT_NAME = "connect";

  private final SseEmitterRepository sseEmitterRepository;

  // 사용자별 SSE 연결을 생성하고 기존 연결이 있으면 새 연결로 교체
  public SseEmitter connect(UUID userId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MILLIS);

    // LastEventId는 재연결 시 누락 이벤트 재전송에 사용할 수 있도록 우선 파라미터만
    if (lastEventId != null) {
      log.debug("SSE reconnect requested. userId={}, lastEventId={}", userId, lastEventId);
    }

    sseEmitterRepository.save(userId, emitter).ifPresent(SseEmitter::complete);
    registerCallbacks(userId, emitter);
    sendConnectEvent(userId, emitter);

    return emitter;
  }

  // 연결 종료, 타임아웃, 에러 발생 시 저장소에서 emitter를 제거
  private void registerCallbacks(UUID userId, SseEmitter emitter) {
    emitter.onCompletion(() -> sseEmitterRepository.delete(userId, emitter));
    emitter.onTimeout(() -> sseEmitterRepository.delete(userId, emitter));
    emitter.onError(error -> sseEmitterRepository.delete(userId, emitter));
  }

  // 연결 직후 클라이언트가 연결 성공 여부를 확인할 수 있도록 초기 이벤트를 전송
  private void sendConnectEvent(UUID userId, SseEmitter emitter) {
    try {
      emitter.send(
          SseEmitter.event()
              .id(UUID.randomUUID().toString())
              .name(CONNECT_EVENT_NAME)
              .data("SSE connection established."));
    } catch (IOException e) {
      sseEmitterRepository.delete(userId, emitter);
      log.warn("Failed to send SSE connect event. userId={}", userId, e);
    }
  }
}
