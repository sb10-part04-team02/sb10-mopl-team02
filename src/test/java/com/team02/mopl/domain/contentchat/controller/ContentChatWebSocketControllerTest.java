package com.team02.mopl.domain.contentchat.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.contentchat.dto.ContentChatDto;
import com.team02.mopl.domain.contentchat.dto.ContentChatSendRequest;
import com.team02.mopl.domain.contentchat.service.ContentChatService;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.global.websocket.redis.StompFanOutPublisher;
import java.security.Principal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentChatWebSocketControllerTest {

  @Mock private ContentChatService contentChatService;
  @Mock private StompFanOutPublisher stompFanOutPublisher;

  @InjectMocks private ContentChatWebSocketController controller;

  @Test
  @DisplayName("메시지 전송 성공 시 올바른 구독 경로로 ContentChatDto가 브로드캐스트된다")
  void sendMessage_success_broadcastsToCorrectDestination() {
    UUID contentId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    ContentChatSendRequest request = new ContentChatSendRequest("안녕하세요");

    ContentChatDto dto = new ContentChatDto(new UserSummary(senderId, "발신자", null), "안녕하세요");

    Principal principal = mockPrincipal(senderId);

    given(contentChatService.createMessage(contentId, senderId, request)).willReturn(dto);

    controller.sendMessage(contentId, request, principal);

    verify(contentChatService).createMessage(contentId, senderId, request);
    verify(stompFanOutPublisher).publish(eq("/sub/contents/" + contentId + "/chat"), eq(dto));
  }

  @Test
  @DisplayName("존재하지 않는 콘텐츠면 예외가 발생하고 브로드캐스트하지 않는다")
  void sendMessage_contentNotFound_throwsAndNoBroadcast() {
    UUID contentId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    ContentChatSendRequest request = new ContentChatSendRequest("안녕하세요");

    Principal principal = mockPrincipal(senderId);

    given(contentChatService.createMessage(contentId, senderId, request))
        .willThrow(new ContentNotFoundException());

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> controller.sendMessage(contentId, request, principal))
        .isInstanceOf(ContentNotFoundException.class);

    verify(stompFanOutPublisher, never()).publish(any(String.class), any(Object.class));
  }

  private Principal mockPrincipal(UUID userId) {
    Principal principal = mock(Principal.class);
    given(principal.getName()).willReturn(userId.toString());
    return principal;
  }
}
