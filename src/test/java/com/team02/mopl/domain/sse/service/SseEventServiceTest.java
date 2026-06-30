package com.team02.mopl.domain.sse.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.team02.mopl.domain.sse.repository.SseEmitterRepository;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseEventServiceTest {

  @Mock private SseEmitterRepository sseEmitterRepository;

  @Mock private SseEmitter emitter;

  @InjectMocks private SseEventService sseEventService;

  @Test
  @DisplayName("연결된 emitter가 있으면 SSE 이벤트를 전송한다")
  void send_connectedEmitter_sendsEvent() throws Exception {
    UUID receiverId = UUID.randomUUID();

    given(sseEmitterRepository.findByUserId(receiverId)).willReturn(Optional.of(emitter));

    sseEventService.send(receiverId, "notifications", UUID.randomUUID().toString(), "data");

    verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
  }

  @Test
  @DisplayName("연결된 emitter가 없으면 이벤트 전송을 시도하지 않는다")
  void send_noEmitter_doesNothing() {
    UUID receiverId = UUID.randomUUID();

    given(sseEmitterRepository.findByUserId(receiverId)).willReturn(Optional.empty());

    sseEventService.send(receiverId, "notifications", UUID.randomUUID().toString(), "data");

    verifyNoInteractions(emitter);
  }

  @Test
  @DisplayName("SSE 이벤트 전송에 실패하면 emitter를 제거한다")
  void send_sendFails_deletesEmitter() throws Exception {
    UUID receiverId = UUID.randomUUID();

    given(sseEmitterRepository.findByUserId(receiverId)).willReturn(Optional.of(emitter));
    doThrow(new IOException()).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

    sseEventService.send(receiverId, "notifications", UUID.randomUUID().toString(), "data");

    verify(sseEmitterRepository).delete(receiverId, emitter);
  }
}
