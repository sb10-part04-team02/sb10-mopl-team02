package com.team02.mopl.domain.dm.controller;

import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSendRequest;
import com.team02.mopl.domain.dm.service.DirectMessageService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DirectMessageWebSocketController {

  private final DirectMessageService directMessageService;
  private final SimpMessagingTemplate messagingTemplate;

  @MessageMapping("/conversations/{conversationId}/direct-messages")
  public void sendDirectMessage(
      @DestinationVariable UUID conversationId,
      @Valid DirectMessageSendRequest request,
      Principal principal) {
    // JWT 인증이 연동되기 전까지 principal이 null일 수 있음 (FIXME: WebSocket JWT 인증 구현 후 제거)
    if (principal == null) {
      log.warn("인증되지 않은 WebSocket 연결에서 DM 전송 시도. conversationId={}", conversationId);
      return;
    }
    UUID senderId = UUID.fromString(principal.getName());
    DirectMessageDto messageDto =
        directMessageService.sendDirectMessage(conversationId, senderId, request);
    messagingTemplate.convertAndSend(
        "/sub/conversations/" + conversationId + "/direct-messages", messageDto);
  }
}
