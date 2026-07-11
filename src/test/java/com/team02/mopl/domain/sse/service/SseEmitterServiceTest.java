package com.team02.mopl.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.team02.mopl.domain.notification.exception.NotificationNotFoundException;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.sse.repository.SseEmitterRepository;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseEmitterServiceTest {

  @Mock private SseEmitterRepository sseEmitterRepository;
  @Mock private NotificationService notificationService;

  @InjectMocks private SseEmitterService sseEmitterService;

  @Test
  @DisplayName("SSE 연결을 생성하면 사용자 ID 기준으로 emitter를 저장한다")
  void connect_success() {
    UUID userId = UUID.randomUUID();

    given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
        .willReturn(Optional.empty());

    SseEmitter result = sseEmitterService.connect(userId, null);

    assertThat(result).isNotNull();

    ArgumentCaptor<SseEmitter> emitterCaptor = ArgumentCaptor.forClass(SseEmitter.class);
    verify(sseEmitterRepository).save(eq(userId), emitterCaptor.capture());
    assertThat(emitterCaptor.getValue()).isSameAs(result);
  }

  @Test
  @DisplayName("기존 SSE 연결이 있으면 기존 emitter를 종료하고 새 emitter로 교체한다")
  void connect_existingEmitter_completesOldEmitter() {
    UUID userId = UUID.randomUUID();
    SseEmitter oldEmitter = mock(SseEmitter.class);

    given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
        .willReturn(Optional.of(oldEmitter));

    SseEmitter result = sseEmitterService.connect(userId, null);

    assertThat(result).isNotNull();
    verify(oldEmitter).complete();
  }

  @Test
  @DisplayName("Last-Event-ID가 있으면 SSE 연결 후 누락 알림 복구를 요청한다")
  void connect_withLastEventId_recoversMissedNotifications() {
    UUID userId = UUID.randomUUID();
    UUID lastNotificationId = UUID.randomUUID();

    given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
        .willReturn(Optional.empty());

    SseEmitter result = sseEmitterService.connect(userId, lastNotificationId.toString());

    assertThat(result).isNotNull();
    verify(notificationService).resendNotificationsAfter(userId, lastNotificationId);
  }

  @Test
  @DisplayName("Last-Event-ID가 없으면 누락 알림 복구를 요청하지 않는다")
  void connect_withoutLastEventId_doesNotRecoverMissedNotifications() {
    UUID userId = UUID.randomUUID();

    given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
        .willReturn(Optional.empty());

    SseEmitter result = sseEmitterService.connect(userId, null);

    assertThat(result).isNotNull();
    verify(notificationService, never()).resendNotificationsAfter(any(), any());
  }

  @Test
  @DisplayName("잘못된 Last-Event-ID 형식이어도 SSE 연결은 유지하고 복구는 건너뛴다")
  void connect_withInvalidLastEventId_doesNotThrow() {
    UUID userId = UUID.randomUUID();

    given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
        .willReturn(Optional.empty());

    SseEmitter result = sseEmitterService.connect(userId, "invalid-event-id");

    assertThat(result).isNotNull();
    verify(notificationService, never()).resendNotificationsAfter(any(), any());
  }

  @Test
  @DisplayName("Last-Event-ID에 해당하는 알림이 없어도 SSE 연결은 유지한다")
  void connect_whenLastNotificationNotFound_doesNotThrow() {
    UUID userId = UUID.randomUUID();
    UUID lastNotificationId = UUID.randomUUID();

    given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
        .willReturn(Optional.empty());
    doThrow(new NotificationNotFoundException())
        .when(notificationService)
        .resendNotificationsAfter(userId, lastNotificationId);

    SseEmitter result = sseEmitterService.connect(userId, lastNotificationId.toString());

    assertThat(result).isNotNull();
    verify(notificationService).resendNotificationsAfter(userId, lastNotificationId);
  }

  @Test
  @DisplayName("SSE 연결 시 완료, 타임아웃, 에러 콜백을 등록한다")
  void connect_registersLifecycleCallbacks() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);
    TestableSseEmitterService service =
        new TestableSseEmitterService(sseEmitterRepository, notificationService, emitter);

    given(sseEmitterRepository.save(userId, emitter)).willReturn(Optional.empty());

    service.connect(userId, null);

    verify(emitter).onCompletion(any(Runnable.class));
    verify(emitter).onTimeout(any(Runnable.class));
    verify(emitter).onError(any());
  }

  @Test
  @DisplayName("SSE 연결 시 초기 connect 이벤트를 전송한다")
  void connect_sendsConnectEvent() throws Exception {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);
    TestableSseEmitterService service =
        new TestableSseEmitterService(sseEmitterRepository, notificationService, emitter);

    given(sseEmitterRepository.save(userId, emitter)).willReturn(Optional.empty());

    service.connect(userId, null);

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  @DisplayName("SSE 연결 완료 콜백이 실행되면 emitter를 제거한다")
  void connect_completionCallback_deletesEmitter() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);
    TestableSseEmitterService service =
        new TestableSseEmitterService(sseEmitterRepository, notificationService, emitter);
    ArgumentCaptor<Runnable> completionCaptor = ArgumentCaptor.forClass(Runnable.class);

    given(sseEmitterRepository.save(userId, emitter)).willReturn(Optional.empty());

    service.connect(userId, null);

    verify(emitter).onCompletion(completionCaptor.capture());

    completionCaptor.getValue().run();

    verify(sseEmitterRepository).delete(userId, emitter);
  }

  @Test
  @DisplayName("SSE 타임아웃 콜백이 실행되면 emitter를 제거한다")
  void connect_timeoutCallback_deletesEmitter() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);
    TestableSseEmitterService service =
        new TestableSseEmitterService(sseEmitterRepository, notificationService, emitter);
    ArgumentCaptor<Runnable> timeoutCaptor = ArgumentCaptor.forClass(Runnable.class);

    given(sseEmitterRepository.save(userId, emitter)).willReturn(Optional.empty());

    service.connect(userId, null);

    verify(emitter).onTimeout(timeoutCaptor.capture());

    timeoutCaptor.getValue().run();

    verify(sseEmitterRepository).delete(userId, emitter);
  }

  @Test
  @DisplayName("SSE 에러 콜백이 실행되면 emitter를 제거한다")
  @SuppressWarnings("unchecked")
  void connect_errorCallback_deletesEmitter() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);
    TestableSseEmitterService service =
        new TestableSseEmitterService(sseEmitterRepository, notificationService, emitter);
    ArgumentCaptor<Consumer<Throwable>> errorCaptor = ArgumentCaptor.forClass(Consumer.class);

    given(sseEmitterRepository.save(userId, emitter)).willReturn(Optional.empty());

    service.connect(userId, null);

    verify(emitter).onError(errorCaptor.capture());

    errorCaptor.getValue().accept(new RuntimeException("SSE error"));

    verify(sseEmitterRepository).delete(userId, emitter);
  }

  @Test
  @DisplayName("초기 connect 이벤트 전송에 실패하면 emitter를 에러로 종료한다")
  void connect_sendConnectEventFails_completesEmitterWithError() throws Exception {
    UUID userId = UUID.randomUUID();
    IOException exception = new IOException("SSE send failed");
    SseEmitter emitter = mock(SseEmitter.class);
    TestableSseEmitterService service =
        new TestableSseEmitterService(sseEmitterRepository, notificationService, emitter);

    given(sseEmitterRepository.save(userId, emitter)).willReturn(Optional.empty());
    doThrow(exception).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

    service.connect(userId, null);

    verify(emitter).completeWithError(exception);
  }

  @Test
  @DisplayName("SSE 연결은 콜백을 등록한 뒤 emitter를 저장한다")
  void connect_registersCallbacksBeforeSave() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = mock(SseEmitter.class);
    TestableSseEmitterService service =
        new TestableSseEmitterService(sseEmitterRepository, notificationService, emitter);

    given(sseEmitterRepository.save(userId, emitter)).willReturn(Optional.empty());

    service.connect(userId, null);

    InOrder inOrder = inOrder(emitter, sseEmitterRepository);
    inOrder.verify(emitter).onCompletion(any(Runnable.class));
    inOrder.verify(emitter).onTimeout(any(Runnable.class));
    inOrder.verify(emitter).onError(any());
    inOrder.verify(sseEmitterRepository).save(userId, emitter);
  }

  private static class TestableSseEmitterService extends SseEmitterService {

    private final SseEmitter emitter;

    private TestableSseEmitterService(
        SseEmitterRepository sseEmitterRepository,
        NotificationService notificationService,
        SseEmitter emitter) {
      super(sseEmitterRepository, notificationService);
      this.emitter = emitter;
    }

    @Override
    protected SseEmitter createEmitter() {
      return emitter;
    }
  }
}
