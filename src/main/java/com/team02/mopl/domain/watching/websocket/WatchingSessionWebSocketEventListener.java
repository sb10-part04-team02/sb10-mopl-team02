package com.team02.mopl.domain.watching.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.watching.dto.WatchingSessionChange;
import com.team02.mopl.domain.watching.service.WatchingSessionService;
import com.team02.mopl.domain.watching.websocket.WatchingSubscriptionRegistry.WatchingSubscription;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorResponse;
import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * 시청 세션 실시간 전파 리스너.
 *
 * <p>클라이언트가 /sub/contents/{contentId}/watch를 SUBSCRIBE하면 시청 세션에 참여한 것으로 보고, UNSUBSCRIBE 또는
 * DISCONNECT(프레임 없이 끊긴 연결 포함) 시 이탈한 것으로 본다. 각 변경은 같은 토픽 구독자 전원에게 브로드캐스트된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionWebSocketEventListener {

  private static final Pattern WATCH_DESTINATION = Pattern.compile("^/sub/contents/([^/]+)/watch$");

  private final WatchingSessionService watchingSessionService;
  private final WatchingSubscriptionRegistry subscriptionRegistry;
  private final SimpMessagingTemplate messagingTemplate;
  // 브로커를 거치지 않고 특정 세션에만 프레임을 내려보내기 위한 채널 (필드명으로 bean 매칭)
  private final MessageChannel clientOutboundChannel;
  private final ObjectMapper objectMapper;

  @EventListener
  public void handleSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String destination = accessor.getDestination();
    if (destination == null) {
      return;
    }
    Matcher matcher = WATCH_DESTINATION.matcher(destination);
    if (!matcher.matches()) {
      return;
    }

    String wsSessionId = accessor.getSessionId();
    String subscriptionId = accessor.getSubscriptionId();
    Principal user = event.getUser();
    if (wsSessionId == null || subscriptionId == null || user == null) {
      return;
    }

    UUID contentId;
    UUID userId;
    try {
      contentId = UUID.fromString(matcher.group(1));
      userId = UUID.fromString(user.getName());
    } catch (IllegalArgumentException e) {
      log.warn("잘못된 시청 세션 구독 대상입니다. destination={}", destination);
      return;
    }

    // 구독 자체는 이미 성립한 뒤라 예외를 던져도 거부할 수 없으므로,
    // 실패 시 로그를 남기고 이미 성립한 구독 위로 실패 사유를 당사자 세션에만 전달한다.
    try {
      WatchingSessionChange change = watchingSessionService.join(contentId, userId);
      subscriptionRegistry.register(
          wsSessionId,
          subscriptionId,
          new WatchingSubscription(change.watchingSession().id(), userId));
      broadcast(contentId, change);
      log.debug("시청 세션 JOIN. contentId={}, userId={}", contentId, userId);
    } catch (Exception e) {
      log.warn("시청 세션 JOIN 처리 실패. contentId={}, userId={}", contentId, userId, e);
      sendErrorToSubscriber(wsSessionId, subscriptionId, destination, e);
    }
  }

  @EventListener
  public void handleUnsubscribe(SessionUnsubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    String wsSessionId = accessor.getSessionId();
    String subscriptionId = accessor.getSubscriptionId();
    if (wsSessionId == null || subscriptionId == null) {
      return;
    }
    subscriptionRegistry.remove(wsSessionId, subscriptionId).ifPresent(this::leaveAndBroadcast);
  }

  @EventListener
  public void handleDisconnect(SessionDisconnectEvent event) {
    // DISCONNECT 이벤트는 중복 발화될 수 있으나 removeAll이 매핑을 비우므로 LEAVE는 한 번만 전파된다.
    subscriptionRegistry.removeAll(event.getSessionId()).forEach(this::leaveAndBroadcast);
  }

  private void leaveAndBroadcast(WatchingSubscription subscription) {
    try {
      watchingSessionService
          .leave(subscription.watchingSessionId(), subscription.userId())
          .ifPresent(change -> broadcast(change.watchingSession().content().id(), change));
    } catch (Exception e) {
      log.warn("시청 세션 LEAVE 처리 실패. watchingSessionId={}", subscription.watchingSessionId(), e);
    }
  }

  private void broadcast(UUID contentId, WatchingSessionChange change) {
    messagingTemplate.convertAndSend("/sub/contents/" + contentId + "/watch", change);
  }

  /**
   * JOIN 실패 사유를 이미 성립한 watch 구독 위로 당사자 세션에만 MESSAGE 프레임으로 내려보낸다.
   *
   * <p>브로커로 보내면 토픽 구독자 전원에게 브로드캐스트되므로, clientOutboundChannel로 세션·구독 ID를 지정해 직접 전송한다. 클라이언트는 별도 에러
   * 채널 구독 없이 기존 watch 콜백으로 ErrorResponse를 수신한다.
   */
  private void sendErrorToSubscriber(
      String wsSessionId, String subscriptionId, String destination, Exception e) {
    try {
      StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
      accessor.setSessionId(wsSessionId);
      accessor.setSubscriptionId(subscriptionId);
      accessor.setDestination(destination);
      accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
      accessor.setLeaveMutable(true);
      // GlobalExceptionHandler와 동일한 노출 정책: 비즈니스 예외만 메시지를 노출하고,
      // 그 외 시스템 예외는 내부 정보가 새지 않도록 일반 메시지로 감춘다.
      ErrorResponse errorResponse =
          e instanceof BusinessException businessException
              ? new ErrorResponse(
                  businessException.getClass().getSimpleName(),
                  businessException.getErrorCode().getMessage(),
                  businessException.getDetails())
              : new ErrorResponse(
                  "InternalServerException",
                  "서버 내부 오류가 발생했습니다.",
                  Map.of("reason", "관리자에게 문의해주세요."));
      byte[] payload = objectMapper.writeValueAsBytes(errorResponse);
      clientOutboundChannel.send(
          MessageBuilder.createMessage(payload, accessor.getMessageHeaders()));
    } catch (Exception sendError) {
      log.warn("시청 세션 JOIN 실패 사유 전송 실패. wsSessionId={}", wsSessionId, sendError);
    }
  }
}
