package com.team02.mopl.global.websocket;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class StompChannelInterceptor implements ChannelInterceptor {
  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {

    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (accessor == null || accessor.getCommand() == null) {
      return message;
    }

    switch (accessor.getCommand()) {
      case CONNECT -> {
        String sessionId = accessor.getSessionId();
        System.out.println("새로운 STOMP 연결: " + sessionId);
      }
      case DISCONNECT -> System.out.println("세션 종료 감지");
      default -> {
        // ignore
      }
    }

    return message;
  }
}
