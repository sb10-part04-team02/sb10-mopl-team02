package com.team02.mopl.global.websocket;

import com.team02.mopl.domain.auth.jwt.JwtAuthenticationProvider;
import com.team02.mopl.domain.auth.jwt.token.JwtAuthenticationToken;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

  private static final Pattern DM_DESTINATION =
      Pattern.compile("^/sub/conversations/([^/]+)/direct-messages$");

  private final WebSocketSessionRegistry sessionRegistry;
  private final ConversationMemberRepository conversationMemberRepository;
  private final JwtAuthenticationProvider jwtAuthenticationProvider;
  private final JwtUtils jwtUtils;

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
      case CONNECT -> handleConnect(message, accessor);
      case DISCONNECT -> handleDisconnect(accessor);
      case SUBSCRIBE -> handleSubscribe(message, accessor);
      default -> {}
    }

    return message;
  }

  private void handleConnect(Message<?> message, StompHeaderAccessor accessor) {
    // 클라이언트는 CONNECT 프레임의 Authorization 헤더로 액세스 토큰을 전달한다.
    String token = jwtUtils.resolveAccessToken(accessor.getFirstNativeHeader("Authorization"));
    if (token == null) {
      throw new MessageDeliveryException(message, "인증 토큰이 필요합니다.");
    }

    Authentication authentication;
    try {
      authentication = jwtAuthenticationProvider.authenticate(new JwtAuthenticationToken(token));
    } catch (AuthenticationException e) {
      log.warn("WebSocket CONNECT 인증 실패: {}", e.getMessage(), e);
      throw new MessageDeliveryException(message, "유효하지 않은 토큰입니다.", e);
    }

    // 이후 SUBSCRIBE/SEND 프레임과 @MessageMapping 핸들러에서 Principal로 사용된다.
    accessor.setUser(authentication);

    String stompSessionId = accessor.getSessionId();
    UUID userId = UUID.fromString(authentication.getName());
    if (stompSessionId != null) {
      sessionRegistry.register(stompSessionId, userId);
    }
    log.info("CONNECT: sessionId={}, userId={}", stompSessionId, userId);
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
    // CONNECT 단계에서 JWT 인증을 강제하므로 principal은 항상 존재한다.
    Principal principal = accessor.getUser();
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
