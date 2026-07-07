package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.event.PlaylistContentAddedEvent;
import com.team02.mopl.domain.subscription.repository.SubscriptionRepository;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class PlaylistContentAddedEventListenerTest {

  @Mock private SubscriptionRepository subscriptionRepository;

  @Mock private NotificationService notificationService;

  @InjectMocks private PlaylistContentAddedEventListener listener;

  @Test
  @DisplayName("플레이리스트 콘텐츠 추가 이벤트를 수신하면 구독자에게 알림을 생성한다")
  void onPlaylistContentAdded_createsNotificationForSubscribers() {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();

    PlaylistContentAddedEvent event =
        new PlaylistContentAddedEvent(playlistId, "내 플리", contentId, "콘텐츠 제목");

    given(subscriptionRepository.findActiveSubscriberIdsByPlaylistId(playlistId))
        .willReturn(List.of(subscriberId));

    listener.onPlaylistContentAdded(event);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);

    then(notificationService).should().createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();

    assertThat(command.receiverId()).isEqualTo(subscriberId);
    assertThat(command.title()).isEqualTo("구독 플레이리스트 콘텐츠 추가 알림");
    assertThat(command.content()).isEqualTo("[내 플리] 플레이리스트에 콘텐츠 제목 콘텐츠가 추가되었습니다.");
    assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(command.notificationType()).isEqualTo(NotificationType.PLAYLIST_CONTENT_ADDED);
  }

  @Test
  @DisplayName("구독자가 없으면 알림을 생성하지 않는다")
  void onPlaylistContentAdded_noSubscribers_doesNotCreateNotification() {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    PlaylistContentAddedEvent event =
        new PlaylistContentAddedEvent(playlistId, "내 플리", contentId, "콘텐츠 제목");

    given(subscriptionRepository.findActiveSubscriberIdsByPlaylistId(playlistId))
        .willReturn(List.of());

    listener.onPlaylistContentAdded(event);

    then(notificationService).should(never()).createNotification(any());
  }

  @Test
  @DisplayName("일부 구독자 알림 생성에 실패해도 예외를 전파하지 않는다")
  void onPlaylistContentAdded_notificationFailure_doesNotThrow() {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();

    PlaylistContentAddedEvent event =
        new PlaylistContentAddedEvent(playlistId, "내 플리", contentId, "콘텐츠 제목");

    given(subscriptionRepository.findActiveSubscriberIdsByPlaylistId(playlistId))
        .willReturn(List.of(subscriberId));
    given(notificationService.createNotification(any()))
        .willThrow(new RuntimeException("notification failed"));

    listener.onPlaylistContentAdded(event);

    then(notificationService).should().createNotification(any());
  }

  @Test
  @DisplayName("플레이리스트 콘텐츠 추가 이벤트 리스너는 알림 저장을 새 트랜잭션에서 처리한다")
  void onPlaylistContentAdded_hasRequiresNewTransaction() throws Exception {
    Method method =
        PlaylistContentAddedEventListener.class.getMethod(
            "onPlaylistContentAdded", PlaylistContentAddedEvent.class);

    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }
}
