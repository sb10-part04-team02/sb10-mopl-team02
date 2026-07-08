package com.team02.mopl.domain.watching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.watching.event.WatchingSessionJoinedEvent;
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
class WatchingSessionJoinedEventListenerTest {

  @Mock private FollowRepository followRepository;
  @Mock private NotificationService notificationService;

  @InjectMocks private WatchingSessionJoinedEventListener listener;

  @Test
  @DisplayName("실시간 시청 시작 이벤트를 수신하면 팔로워에게 주요 활동 알림을 생성한다")
  void onWatchingSessionJoined_createsFollowingUserActivityNotification() {
    UUID watcherId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    WatchingSessionJoinedEvent event =
        new WatchingSessionJoinedEvent(watcherId, "우디", contentId, "QA 영화");

    given(followRepository.findActiveFollowerIdsByFolloweeId(watcherId))
        .willReturn(List.of(followerId));

    listener.onWatchingSessionJoined(event);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);

    then(notificationService).should(times(1)).createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();

    assertThat(command.receiverId()).isEqualTo(followerId);
    assertThat(command.title()).isEqualTo("우디님이 콘텐츠를 시청하기 시작했어요.");
    assertThat(command.content()).isEqualTo("[QA 영화] 시청 중");
    assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(command.notificationType()).isEqualTo(NotificationType.FOLLOWING_USER_ACTIVITY);
  }

  @Test
  @DisplayName("팔로워가 없으면 알림을 생성하지 않는다")
  void onWatchingSessionJoined_noFollowers_doesNotCreateNotification() {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    WatchingSessionJoinedEvent event =
        new WatchingSessionJoinedEvent(watcherId, "우디", contentId, "QA 영화");

    given(followRepository.findActiveFollowerIdsByFolloweeId(watcherId)).willReturn(List.of());

    listener.onWatchingSessionJoined(event);

    then(notificationService).should(never()).createNotification(any());
  }

  @Test
  @DisplayName("일부 팔로워 알림 생성에 실패해도 예외를 전파하지 않는다")
  void onWatchingSessionJoined_notificationFailure_doesNotPropagate() {
    UUID watcherId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    WatchingSessionJoinedEvent event =
        new WatchingSessionJoinedEvent(watcherId, "우디", contentId, "QA 영화");

    given(followRepository.findActiveFollowerIdsByFolloweeId(watcherId))
        .willReturn(List.of(followerId));
    given(notificationService.createNotification(any()))
        .willThrow(new RuntimeException("notification failed"));

    assertDoesNotThrow(() -> listener.onWatchingSessionJoined(event));

    then(notificationService).should(times(1)).createNotification(any());
  }
}
