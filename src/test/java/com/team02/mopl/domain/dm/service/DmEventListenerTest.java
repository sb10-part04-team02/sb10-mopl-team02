package com.team02.mopl.domain.dm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DmSentEvent;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.sse.service.SseEventService;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DmEventListenerTest {

  @Mock private SseEventService sseEventService;

  @Mock private NotificationService notificationService;

  @InjectMocks private DmEventListener dmEventListener;

  @Test
  @DisplayName("DM 전송 이벤트를 수신하면 알림 생성과 direct-messages SSE 전송을 수행한다")
  void onDmSent_createsNotificationAndSendsSse() {
    UUID messageId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();
    String eventId = messageId.toString();

    DirectMessageDto dto =
        new DirectMessageDto(
            messageId,
            conversationId,
            Instant.parse("2026-07-01T00:00:00Z"),
            new UserSummary(senderId, "발신자", null),
            new UserSummary(receiverId, "수신자", null),
            "안녕하세요");

    DmSentEvent event = new DmSentEvent(receiverId, eventId, dto);

    dmEventListener.onDmSent(event);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);

    verify(notificationService).createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();

    assertThat(command.receiverId()).isEqualTo(receiverId);
    assertThat(command.title()).isEqualTo("새 메시지");
    assertThat(command.content()).isEqualTo("발신자님이 메시지를 보냈습니다.");
    assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(command.notificationType()).isEqualTo(NotificationType.DIRECT_MESSAGE_RECEIVED);

    verify(sseEventService).send(eq(receiverId), eq("direct-messages"), eq(eventId), eq(dto));
  }
}
