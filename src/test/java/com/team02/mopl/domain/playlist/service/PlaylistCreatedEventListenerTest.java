package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.event.PlaylistCreatedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaylistCreatedEventListenerTest {

  @Mock private FollowRepository followRepository;

  @Mock private NotificationService notificationService;

  @InjectMocks private PlaylistCreatedEventListener listener;

  @Test
  @DisplayName("플레이리스트 생성 이벤트를 수신하면 팔로워에게 주요 활동 알림을 생성한다")
  void onPlaylistCreated_createsFollowingUserActivityNotification() {
    UUID ownerId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(ownerId, "우디", "내 플리", "설명");

    given(followRepository.findActiveFollowerIdsByFolloweeId(ownerId))
        .willReturn(List.of(followerId));

    listener.onPlaylistCreated(event);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);

    then(notificationService).should().createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();

    assertThat(command.receiverId()).isEqualTo(followerId);
    assertThat(command.title()).isEqualTo("우디님이 플레이리스트를 만들었어요.");
    assertThat(command.content()).isEqualTo("[내 플리] 설명");
    assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(command.notificationType()).isEqualTo(NotificationType.FOLLOWING_USER_ACTIVITY);
  }

  @Test
  @DisplayName("팔로워가 없으면 알림을 생성하지 않는다")
  void onPlaylistCreated_noFollowers_doesNotCreateNotification() {
    UUID ownerId = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(ownerId, "우디", "내 플리", "설명");

    given(followRepository.findActiveFollowerIdsByFolloweeId(ownerId)).willReturn(List.of());

    listener.onPlaylistCreated(event);

    then(notificationService).should(never()).createNotification(any());
  }

  @Test
  @DisplayName("팔로워 알림 생성에 실패해도 예외를 전파하지 않는다")
  void onPlaylistCreated_notificationFailure_doesNotStopListener() {
    UUID ownerId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(ownerId, "우디", "내 플리", "설명");

    given(followRepository.findActiveFollowerIdsByFolloweeId(ownerId))
        .willReturn(List.of(followerId));
    given(notificationService.createNotification(any()))
        .willThrow(new RuntimeException("notification failed"));

    listener.onPlaylistCreated(event);

    then(notificationService).should().createNotification(any());
  }
}
