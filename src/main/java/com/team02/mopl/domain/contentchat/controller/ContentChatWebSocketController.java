package com.team02.mopl.domain.contentchat.controller;

import com.team02.mopl.domain.contentchat.dto.ContentChatDto;
import com.team02.mopl.domain.contentchat.dto.ContentChatSendRequest;
import com.team02.mopl.domain.contentchat.service.ContentChatService;
import com.team02.mopl.global.exception.ErrorResponse;
import com.team02.mopl.global.websocket.redis.StompFanOutPublisher;
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
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ContentChatWebSocketController {

  private final ContentChatService contentChatService;
  private final StompFanOutPublisher stompFanOutPublisher;

  @MessageMapping("/contents/{contentId}/chat")
  public void sendMessage(
      @DestinationVariable UUID contentId,
      @Valid ContentChatSendRequest request,
      Principal principal) {
    // CONNECT 단계에서 JWT 인증을 강제하므로 principal은 항상 존재한다.
    UUID senderId = UUID.fromString(principal.getName());
    ContentChatDto message = contentChatService.createMessage(contentId, senderId, request);
    stompFanOutPublisher.publish("/sub/contents/" + contentId + "/chat", message);
  }

  // @Valid 페이로드 검증 실패는 messaging 모듈의 MethodArgumentNotValidException으로 전달된다.
  @MessageExceptionHandler(MethodArgumentNotValidException.class)
  @SendToUser("/queue/errors")
  public ErrorResponse handleValidation(MethodArgumentNotValidException e) {
    BindingResult bindingResult = e.getBindingResult();
    Map<String, String> details =
        bindingResult == null
            ? Map.of()
            : bindingResult.getFieldErrors().stream()
                .collect(
                    Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage(),
                        (a, b) -> a));
    return new ErrorResponse(e.getClass().getSimpleName(), "입력값 검증에 실패했습니다.", details);
  }

  @MessageExceptionHandler(Exception.class)
  @SendToUser("/queue/errors")
  public ErrorResponse handleException(Exception e) {
    // 내부 예외 메시지가 클라이언트에 노출되지 않도록 일반화된 메시지만 반환한다.
    log.warn("콘텐츠 채팅 메시지 처리 중 예외 발생: {}", e.getMessage(), e);
    return new ErrorResponse(e.getClass().getSimpleName(), "메시지 처리 중 오류가 발생했습니다.", Map.of());
  }
}
