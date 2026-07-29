package com.team02.mopl.domain.dm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSendRequest;
import com.team02.mopl.domain.dm.exception.ConversationForbiddenException;
import com.team02.mopl.domain.dm.service.DirectMessageService;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.global.websocket.redis.StompFanOutPublisher;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DirectMessageWebSocketControllerTest {

  @Mock private DirectMessageService directMessageService;
  @Mock private StompFanOutPublisher stompFanOutPublisher;

  @InjectMocks private DirectMessageWebSocketController controller;

  @Test
  @DisplayName("메시지 전송 성공 시 올바른 구독 경로로 DirectMessageDto가 브로드캐스트된다")
  void sendDirectMessage_success_broadcastsToCorrectDestination() {
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요");

    DirectMessageDto dto =
        new DirectMessageDto(
            UUID.randomUUID(),
            conversationId,
            Instant.now(),
            new UserSummary(senderId, "발신자", null),
            new UserSummary(receiverId, "수신자", null),
            "안녕하세요");

    Principal principal = mockPrincipal(senderId);

    given(directMessageService.sendDirectMessage(conversationId, senderId, request))
        .willReturn(dto);

    controller.sendDirectMessage(conversationId, request, principal);

    verify(directMessageService).sendDirectMessage(conversationId, senderId, request);
    verify(stompFanOutPublisher)
        .publish(eq("/sub/conversations/" + conversationId + "/direct-messages"), eq(dto));
  }

  @Test
  @DisplayName("발신자가 대화방 멤버가 아니면 FORBIDDEN 예외가 발생하고 브로드캐스트하지 않는다")
  void sendDirectMessage_notMember_throwsForbiddenAndNoBroadcast() {
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요");

    Principal principal = mockPrincipal(senderId);

    given(directMessageService.sendDirectMessage(conversationId, senderId, request))
        .willThrow(new ConversationForbiddenException());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> controller.sendDirectMessage(conversationId, request, principal))
        .isInstanceOf(ConversationForbiddenException.class);

    verify(stompFanOutPublisher, never()).publish(any(String.class), any(Object.class));
  }

  private Principal mockPrincipal(UUID userId) {
    Principal principal = mock(Principal.class);
    given(principal.getName()).willReturn(userId.toString());
    return principal;
  }
}
