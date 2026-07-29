package com.team02.mopl.domain.dm.controller;

import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSendRequest;
import com.team02.mopl.domain.dm.exception.ConversationForbiddenException;
import com.team02.mopl.domain.dm.service.DirectMessageService;
import com.team02.mopl.global.exception.ErrorResponse;
import com.team02.mopl.global.websocket.redis.StompFanOutPublisher;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

  private final DirectMessageService directMessageService;
  private final StompFanOutPublisher stompFanOutPublisher;

  @MessageMapping("/conversations/{conversationId}/direct-messages")
  public void sendDirectMessage(
      @DestinationVariable UUID conversationId,
      @Valid DirectMessageSendRequest request,
      Principal principal) {
    // CONNECT 단계에서 JWT 인증을 강제하므로 principal은 항상 존재한다.
    UUID senderId = UUID.fromString(principal.getName());
    DirectMessageDto messageDto =
        directMessageService.sendDirectMessage(conversationId, senderId, request);
    stompFanOutPublisher.publish(
        "/sub/conversations/" + conversationId + "/direct-messages", messageDto);
  }

  @MessageExceptionHandler(ConversationForbiddenException.class)
  @SendToUser("/queue/errors")
  public ErrorResponse handleForbidden(ConversationForbiddenException e) {
    return new ErrorResponse(e.getClass().getSimpleName(), e.getMessage(), Map.of());
  }

  @MessageExceptionHandler(ConstraintViolationException.class)
  @SendToUser("/queue/errors")
  public ErrorResponse handleValidation(ConstraintViolationException e) {
    Map<String, String> details =
        e.getConstraintViolations().stream()
            .collect(
                Collectors.toMap(
                    v -> v.getPropertyPath().toString(), v -> v.getMessage(), (a, b) -> a));
    return new ErrorResponse(e.getClass().getSimpleName(), "입력값 검증에 실패했습니다.", details);
  }

  @MessageExceptionHandler(Exception.class)
  @SendToUser("/queue/errors")
  public ErrorResponse handleException(Exception e) {
    log.warn("WebSocket 메시지 처리 중 예외 발생: {}", e.getMessage(), e);
    return new ErrorResponse(e.getClass().getSimpleName(), e.getMessage(), Map.of());
  }
}
