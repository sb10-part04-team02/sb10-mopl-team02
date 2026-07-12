package com.team02.mopl.domain.sse.service;

import com.team02.mopl.domain.notification.exception.NotificationNotFoundException;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.sse.repository.SseEmitterRepository;
import java.io.IOException;
import java.util.Optional;
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
  private final NotificationService notificationService;

  // 사용자별 SSE 연결을 생성하고 기존 연결이 있으면 새 연결로 교체
  public SseEmitter connect(UUID userId, String lastEventId) {
    SseEmitter emitter = createEmitter();

    // LastEventId는 재연결 시 누락 이벤트 재전송에 사용할 수 있도록 우선 파라미터만
    if (lastEventId != null) {
      log.debug("SSE 재연결 요청. userId={}, lastEventId={}", userId, lastEventId);
    }

    registerCallbacks(userId, emitter);
    sseEmitterRepository.save(userId, emitter).ifPresent(SseEmitter::complete);
    sendConnectEvent(userId, emitter);
    recoverMissedNotifications(userId, lastEventId);

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
              .id(SseEventId.connect(UUID.randomUUID()))
              .name(CONNECT_EVENT_NAME)
              .data("SSE 연결 성공"));
    } catch (IOException e) {
      emitter.completeWithError(e);
      log.warn("SSE 연결 이벤트 전송 실패. userId={}", userId, e);
    }
  }

  // 복구만 skip, 연결은 유지
  private void recoverMissedNotifications(UUID userId, String lastEventId) {
    if (lastEventId == null || lastEventId.isBlank()) {
      return;
    }

    try {
      Optional<UUID> lastNotificationId = SseEventId.parseNotificationId(lastEventId);

      if (lastNotificationId.isEmpty()) {
        log.debug(
            "알림 이벤트가 아닌 Last-Event-ID이므로 알림 복구를 건너뜁니다. userId={}, lastEventId={}",
            userId,
            lastEventId);
        return;
      }

      notificationService.resendNotificationsAfter(userId, lastNotificationId.get());
    } catch (IllegalArgumentException e) {
      log.warn("잘못된 SSE Last-Event-ID 형식입니다. userId={}, lastEventId={}", userId, lastEventId, e);
    } catch (NotificationNotFoundException e) {
      log.warn(
          "SSE Last-Event-ID에 해당하는 알림을 찾을 수 없습니다. userId={}, lastEventId={}",
          userId,
          lastEventId,
          e);
    } catch (RuntimeException e) {
      log.warn("SSE 누락 알림 복구 중 예외가 발생했습니다. userId={}, lastEventId={}", userId, lastEventId, e);
    }
  }

  protected SseEmitter createEmitter() {
    return new SseEmitter(DEFAULT_TIMEOUT_MILLIS);
  }
}
