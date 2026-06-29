package com.team02.mopl.domain.dm.controller;

import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSendRequest;
import com.team02.mopl.domain.dm.service.DirectMessageService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

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
    UUID senderId = UUID.fromString(principal.getName());
    DirectMessageDto messageDto =
        directMessageService.sendDirectMessage(conversationId, senderId, request);
    messagingTemplate.convertAndSend(
        "/sub/conversations/" + conversationId + "/direct-messages", messageDto);
  }
}
