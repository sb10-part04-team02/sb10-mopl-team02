package com.team02.mopl.domain.sse.controller;

import com.team02.mopl.domain.sse.service.SseEmitterService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController implements SseApi {

  private final SseEmitterService sseEmitterService;

  // 인증된 사용자의 SSE 연결을 생성
  @Override
  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> connect(
      @AuthenticationPrincipal UUID userId,
      @RequestParam(name = "LastEventId", required = false) UUID lastEventId) {
    return ResponseEntity.ok(sseEmitterService.connect(userId, lastEventId));
  }
}
