package com.team02.mopl.domain.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.subscription.event.SubscriptionCreatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionCreatedEventListenerTest {

  @Mock private NotificationService notificationService;

  @InjectMocks private SubscriptionCreatedEventListener listener;

  @Test
  @DisplayName("구독 생성 이벤트를 수신하면 플레이리스트 소유자에게 구독 알림을 생성한다")
  void onSubscriptionCreated_createsPlaylistSubscribedNotification() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistOwnerId = UUID.randomUUID();

    SubscriptionCreatedEvent event =
        new SubscriptionCreatedEvent(subscriberId, "구독자", playlistOwnerId, "내 플리");

    listener.onSubscriptionCreated(event);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);

    then(notificationService).should().createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();

    assertThat(command.receiverId()).isEqualTo(playlistOwnerId);
    assertThat(command.title()).isEqualTo("플레이리스트 구독 알림");
    assertThat(command.content()).isEqualTo("구독자님이 [내 플리] 플레이리스트를 구독했습니다.");
    assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(command.notificationType()).isEqualTo(NotificationType.PLAYLIST_SUBSCRIBED);
  }

  @Test
  @DisplayName("구독 알림 생성에 실패해도 예외를 전파하지 않는다")
  void onSubscriptionCreated_notificationFailure_doesNotThrow() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistOwnerId = UUID.randomUUID();

    SubscriptionCreatedEvent event =
        new SubscriptionCreatedEvent(subscriberId, "구독자", playlistOwnerId, "내 플리");

    given(notificationService.createNotification(org.mockito.ArgumentMatchers.any()))
        .willThrow(new RuntimeException("notification failed"));

    listener.onSubscriptionCreated(event);

    then(notificationService).should().createNotification(org.mockito.ArgumentMatchers.any());
  }
}
