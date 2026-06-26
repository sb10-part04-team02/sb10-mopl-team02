package com.team02.mopl.global.websocket;

import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

  private final WebSocketSessionRegistry sessionRegistry;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {

    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null) {
      return message;
    }

    StompCommand command = accessor.getCommand();

    if (command == null) {
      return message;
    }

    switch (command) {
      case CONNECT -> handleConnect(accessor);
      case DISCONNECT -> handleDisconnect(accessor);
      default -> {}
    }

    return message;
  }

  private void handleConnect(StompHeaderAccessor accessor) {
    String stompSessionId = accessor.getSessionId();
    Principal principal = accessor.getUser();

    if (stompSessionId == null || principal == null) {
      return;
    }

    try {
      UUID userId = UUID.fromString(principal.getName());
      sessionRegistry.register(stompSessionId, userId);
      log.info("CONNECT: sessionId={}, userId={}", stompSessionId, userId);
    } catch (IllegalArgumentException e) {
      log.warn("CONNECT: principal이 UUID 형식이 아님 — {}", principal.getName());
    }
  }

  private void handleDisconnect(StompHeaderAccessor accessor) {
    String stompSessionId = accessor.getSessionId();

    if (stompSessionId == null) {
      return;
    }

    sessionRegistry
        .getUserId(stompSessionId)
        .ifPresent(
            userId -> {
              sessionRegistry.remove(stompSessionId);
              log.info("DISCONNECT: sessionId={}, userId={} 세션 정리 완료", stompSessionId, userId);
            });
  }
}
