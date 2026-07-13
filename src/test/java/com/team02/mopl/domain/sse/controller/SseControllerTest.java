package com.team02.mopl.domain.sse.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.sse.service.SseEmitterService;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(SseController.class)
@Import(TestSecurityConfiguration.class)
class SseControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SseEmitterService sseEmitterService;

  @Test
  @DisplayName("SSE 연결 요청이 성공하면 비동기 연결을 시작한다")
  void connect_success() throws Exception {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter();

    given(sseEmitterService.connect(userId, null)).willReturn(emitter);

    mockMvc
        .perform(
            get("/api/sse")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .with(authentication(authenticationWithPrincipal(userId))))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());

    verify(sseEmitterService).connect(userId, null);
  }

  @Test
  @DisplayName("LastEventId 쿼리 파라미터가 있으면 서비스로 전달한다")
  void connect_withLastEventIdParam() throws Exception {
    UUID userId = UUID.randomUUID();
    String lastEventId = "notification:" + UUID.randomUUID();
    SseEmitter emitter = new SseEmitter();

    given(sseEmitterService.connect(userId, lastEventId)).willReturn(emitter);

    mockMvc
        .perform(
            get("/api/sse")
                .param("LastEventId", lastEventId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .with(authentication(authenticationWithPrincipal(userId))))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());

    verify(sseEmitterService).connect(userId, lastEventId);
  }

  @Test
  @DisplayName("Last-Event-ID 헤더가 있으면 서비스로 전달한다")
  void connect_withLastEventIdHeader() throws Exception {
    UUID userId = UUID.randomUUID();
    String lastEventId = "notification:" + UUID.randomUUID();
    SseEmitter emitter = new SseEmitter();

    given(sseEmitterService.connect(userId, lastEventId)).willReturn(emitter);

    mockMvc
        .perform(
            get("/api/sse")
                .header("Last-Event-ID", lastEventId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .with(authentication(authenticationWithPrincipal(userId))))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());

    verify(sseEmitterService).connect(userId, lastEventId);
  }

  @Test
  @DisplayName("쿼리 파라미터와 헤더가 모두 있으면 Last-Event-ID 헤더를 우선 사용한다")
  void connect_whenParamAndHeaderExist_usesHeaderFirst() throws Exception {
    UUID userId = UUID.randomUUID();
    String paramLastEventId = "notification:" + UUID.randomUUID();
    String headerLastEventId = "direct-message:" + UUID.randomUUID();
    SseEmitter emitter = new SseEmitter();

    given(sseEmitterService.connect(userId, headerLastEventId)).willReturn(emitter);

    mockMvc
        .perform(
            get("/api/sse")
                .param("LastEventId", paramLastEventId)
                .header("Last-Event-ID", headerLastEventId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .with(authentication(authenticationWithPrincipal(userId))))
        .andExpect(status().isOk())
        .andExpect(request().asyncStarted());

    verify(sseEmitterService).connect(userId, headerLastEventId);
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
