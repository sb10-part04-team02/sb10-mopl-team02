package com.team02.mopl.global.websocket;

import com.team02.mopl.domain.dm.repository.ConversationMemberRepository;
import java.security.Principal;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

  private static final Pattern DM_DESTINATION =
      Pattern.compile("^/sub/conversations/([^/]+)/direct-messages$");

  private final WebSocketSessionRegistry sessionRegistry;
  private final ConversationMemberRepository conversationMemberRepository;

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
      case SUBSCRIBE -> handleSubscribe(message, accessor);
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

  private void handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    if (destination == null) {
      return;
    }
    Matcher matcher = DM_DESTINATION.matcher(destination);
    if (!matcher.matches()) {
      return;
    }
    Principal principal = accessor.getUser();
    // JWT 미연동 상태에서는 principal이 null — 구독 허용 (FIXME: WebSocket JWT 인증 구현 후 제거)
    if (principal == null) {
      log.warn("인증되지 않은 연결의 DM 구독 시도. destination={}", destination);
      return;
    }
    UUID conversationId;
    UUID userId;
    try {
      conversationId = UUID.fromString(matcher.group(1));
      userId = UUID.fromString(principal.getName());
    } catch (IllegalArgumentException e) {
      throw new MessageDeliveryException(message, "잘못된 구독 대상입니다.");
    }
    boolean isMember =
        conversationMemberRepository
            .findByConversationIdAndUserId(conversationId, userId)
            .isPresent();
    if (!isMember) {
      log.warn("대화방 비멤버의 DM 구독 시도 차단. conversationId={}, userId={}", conversationId, userId);
      throw new MessageDeliveryException(message, "해당 대화방에 접근할 권한이 없습니다.");
    }
    log.debug("DM 구독 허용. conversationId={}, userId={}", conversationId, userId);
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
