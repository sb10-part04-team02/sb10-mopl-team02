package com.team02.mopl.domain.sse.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "sse-controller")
public interface SseApi {

  @Operation(summary = "SSE 연결", description = "인증된 사용자의 실시간 이벤트 수신 연결을 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "OK"),
  })
  ResponseEntity<SseEmitter> connect(
      @Parameter(hidden = true) UUID userId,
      @Parameter(description = "마지막으로 수신한 이벤트 ID")
          @RequestParam(name = "LastEventId", required = false)
          String lastEventIdParam,
      @Parameter(hidden = true) @RequestHeader(name = "Last-Event-ID", required = false)
          String lastEventIdHeader);
}
