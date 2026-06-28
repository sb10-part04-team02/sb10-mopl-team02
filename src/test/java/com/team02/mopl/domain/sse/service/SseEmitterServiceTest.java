package com.team02.mopl.domain.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.team02.mopl.domain.sse.repository.SseEmitterRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseEmitterServiceTest {

  @Mock private SseEmitterRepository sseEmitterRepository;

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
  @DisplayName("LastEventId가 있어도 SSE 연결을 생성한다")
  void connect_withLastEventId_success() {
    UUID userId = UUID.randomUUID();
    String lastEventId = UUID.randomUUID().toString();

    given(sseEmitterRepository.save(eq(userId), any(SseEmitter.class)))
        .willReturn(Optional.empty());

    SseEmitter result = sseEmitterService.connect(userId, lastEventId);

    assertThat(result).isNotNull();
    verify(sseEmitterRepository).save(eq(userId), any(SseEmitter.class));
  }
}
