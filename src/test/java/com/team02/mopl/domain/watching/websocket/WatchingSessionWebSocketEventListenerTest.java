package com.team02.mopl.domain.watching.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.watching.dto.WatchingSessionChange;
import com.team02.mopl.domain.watching.dto.WatchingSessionDto;
import com.team02.mopl.domain.watching.enums.ChangeType;
import com.team02.mopl.domain.watching.service.WatchingSessionService;
import com.team02.mopl.global.exception.ErrorResponse;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

@ExtendWith(MockitoExtension.class)
class WatchingSessionWebSocketEventListenerTest {

  @Mock private WatchingSessionService watchingSessionService;
  @Mock private SimpMessagingTemplate messagingTemplate;

  private WatchingSubscriptionRegistry subscriptionRegistry;
  private WatchingSessionWebSocketEventListener listener;

  private final UUID contentId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID watchingSessionId = UUID.randomUUID();
  private final Principal principal = userId::toString;

  @BeforeEach
  void setUp() {
    subscriptionRegistry = new WatchingSubscriptionRegistry();
    listener =
        new WatchingSessionWebSocketEventListener(
            watchingSessionService, subscriptionRegistry, messagingTemplate);
  }

  @Test
  @DisplayName("watch 토픽 구독 시 join 결과를 해당 토픽으로 브로드캐스트한다")
  void handleSubscribe_watchDestination_joinsAndBroadcasts() {
    // given
    WatchingSessionChange change = change(ChangeType.JOIN, 1L);
    given(watchingSessionService.join(contentId, userId)).willReturn(change);

    // when
    listener.handleSubscribe(subscribeEvent("ws1", "sub1", watchDestination()));

    // then
    verify(messagingTemplate).convertAndSend(watchDestination(), change);
  }

  @Test
  @DisplayName("watch 토픽이 아닌 구독은 무시한다")
  void handleSubscribe_otherDestination_ignored() {
    // when
    listener.handleSubscribe(subscribeEvent("ws1", "sub1", "/sub/contents/" + contentId + "/chat"));

    // then
    verifyNoInteractions(watchingSessionService, messagingTemplate);
  }

  @Test
  @DisplayName("contentId가 UUID가 아니면 무시한다")
  void handleSubscribe_invalidContentId_ignored() {
    // when
    listener.handleSubscribe(subscribeEvent("ws1", "sub1", "/sub/contents/not-a-uuid/watch"));

    // then
    verifyNoInteractions(watchingSessionService, messagingTemplate);
  }

  @Test
  @DisplayName("join이 실패하면 구독자에게 에러를 전송하고, 브로드캐스트나 세션 추적은 하지 않는다")
  void handleSubscribe_joinFails_sendsErrorNoBroadcastNoTracking() {
    // given
    given(watchingSessionService.join(contentId, userId)).willThrow(new ContentNotFoundException());

    // when
    listener.handleSubscribe(subscribeEvent("ws1", "sub1", watchDestination()));
    listener.handleDisconnect(disconnectEvent("ws1"));

    // then
    ArgumentCaptor<ErrorResponse> errorCaptor = ArgumentCaptor.forClass(ErrorResponse.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq(userId.toString()), eq("/queue/errors"), errorCaptor.capture());
    assertThat(errorCaptor.getValue().exceptionName()).isEqualTo("ContentNotFoundException");
    verifyNoMoreInteractions(messagingTemplate);
    verify(watchingSessionService).join(contentId, userId);
    verifyNoMoreInteractions(watchingSessionService);
  }

  @Test
  @DisplayName("구독 해제 시 추적 중인 세션을 leave하고 LEAVE를 브로드캐스트한다")
  void handleUnsubscribe_trackedSubscription_leavesAndBroadcasts() {
    // given
    givenJoined("ws1", "sub1");
    WatchingSessionChange leaveChange = change(ChangeType.LEAVE, 0L);
    given(watchingSessionService.leave(watchingSessionId, userId))
        .willReturn(Optional.of(leaveChange));

    // when
    listener.handleUnsubscribe(unsubscribeEvent("ws1", "sub1"));

    // then
    verify(watchingSessionService).leave(watchingSessionId, userId);
    verify(messagingTemplate).convertAndSend(watchDestination(), leaveChange);
  }

  @Test
  @DisplayName("추적하지 않는 구독 해제는 무시하고, 같은 구독 해제가 중복돼도 leave는 한 번만 호출한다")
  void handleUnsubscribe_untrackedOrDuplicate_ignored() {
    // given
    givenJoined("ws1", "sub1");
    given(watchingSessionService.leave(watchingSessionId, userId)).willReturn(Optional.empty());

    // when
    listener.handleUnsubscribe(unsubscribeEvent("ws1", "unknown-sub"));
    listener.handleUnsubscribe(unsubscribeEvent("ws1", "sub1"));
    listener.handleUnsubscribe(unsubscribeEvent("ws1", "sub1")); // 중복

    // then
    verify(watchingSessionService, times(1)).leave(watchingSessionId, userId);
  }

  @Test
  @DisplayName("연결 종료 시 해당 연결이 추적하던 모든 세션을 leave하고 브로드캐스트한다")
  void handleDisconnect_leavesAllTrackedSessions() {
    // given
    givenJoined("ws1", "sub1");
    WatchingSessionChange leaveChange = change(ChangeType.LEAVE, 0L);
    given(watchingSessionService.leave(watchingSessionId, userId))
        .willReturn(Optional.of(leaveChange));

    // when
    listener.handleDisconnect(disconnectEvent("ws1"));
    listener.handleDisconnect(disconnectEvent("ws1")); // 중복 발화

    // then
    verify(watchingSessionService, times(1)).leave(watchingSessionId, userId);
    verify(messagingTemplate).convertAndSend(watchDestination(), leaveChange);
  }

  @Test
  @DisplayName("leave가 empty를 반환하면 브로드캐스트하지 않는다")
  void handleUnsubscribe_leaveEmpty_noBroadcast() {
    // given
    givenJoined("ws1", "sub1");
    given(watchingSessionService.leave(watchingSessionId, userId)).willReturn(Optional.empty());

    // when
    listener.handleUnsubscribe(unsubscribeEvent("ws1", "sub1"));

    // then
    verifyNoMoreInteractions(messagingTemplate);
  }

  @Test
  @DisplayName("leave 처리 중 예외가 발생해도 예외를 전파하지 않고 브로드캐스트하지 않는다")
  void handleUnsubscribe_leaveThrows_doesNotPropagate() {
    // given
    givenJoined("ws1", "sub1");
    given(watchingSessionService.leave(watchingSessionId, userId))
        .willThrow(new RuntimeException("boom"));

    // when & then
    assertThatCode(() -> listener.handleUnsubscribe(unsubscribeEvent("ws1", "sub1")))
        .doesNotThrowAnyException();
    verifyNoMoreInteractions(messagingTemplate);
  }

  // 구독(JOIN)까지 마친 상태를 만든다. 이때 발생한 JOIN 브로드캐스트 상호작용은 검증 대상에서 제외한다.
  private void givenJoined(String wsSessionId, String subscriptionId) {
    WatchingSessionChange joinChange = change(ChangeType.JOIN, 1L);
    given(watchingSessionService.join(contentId, userId)).willReturn(joinChange);
    listener.handleSubscribe(subscribeEvent(wsSessionId, subscriptionId, watchDestination()));
    verify(messagingTemplate).convertAndSend(watchDestination(), joinChange);
  }

  private String watchDestination() {
    return "/sub/contents/" + contentId + "/watch";
  }

  private WatchingSessionChange change(ChangeType type, long watcherCount) {
    ContentSummary content =
        new ContentSummary(contentId, ContentType.MOVIE, "제목", "설명", null, List.of(), 0.0, 0);
    WatchingSessionDto dto =
        new WatchingSessionDto(watchingSessionId, Instant.now(), null, content);
    return new WatchingSessionChange(type, dto, watcherCount);
  }

  private SessionSubscribeEvent subscribeEvent(
      String wsSessionId, String subscriptionId, String destination) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
    accessor.setSessionId(wsSessionId);
    accessor.setSubscriptionId(subscriptionId);
    accessor.setDestination(destination);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    return new SessionSubscribeEvent(this, message, principal);
  }

  private SessionUnsubscribeEvent unsubscribeEvent(String wsSessionId, String subscriptionId) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
    accessor.setSessionId(wsSessionId);
    accessor.setSubscriptionId(subscriptionId);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    return new SessionUnsubscribeEvent(this, message, principal);
  }

  private SessionDisconnectEvent disconnectEvent(String wsSessionId) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
    accessor.setSessionId(wsSessionId);
    Message<byte[]> message =
        MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    return new SessionDisconnectEvent(this, message, wsSessionId, CloseStatus.NORMAL);
  }
}
