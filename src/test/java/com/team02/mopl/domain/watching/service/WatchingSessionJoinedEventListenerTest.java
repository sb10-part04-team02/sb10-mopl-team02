package com.team02.mopl.domain.watching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import com.team02.mopl.domain.watching.event.WatchingSessionJoinedEvent;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class WatchingSessionJoinedEventListenerTest {

  @Mock private FollowRepository followRepository;
  @Mock private NotificationKafkaProducer notificationKafkaProducer;

  @InjectMocks private WatchingSessionJoinedEventListener listener;

  @Test
  @DisplayName("실시간 시청 시작 이벤트를 수신하면 팔로워에게 주요 활동 알림 Kafka 메시지를 발행한다")
  void onWatchingSessionJoined_publishesFollowingUserActivityKafkaMessage() {
    UUID watcherId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    WatchingSessionJoinedEvent event =
        new WatchingSessionJoinedEvent(watcherId, "우디", contentId, "QA 영화");

    given(followRepository.findActiveFollowerIdsByFolloweeId(watcherId))
        .willReturn(List.of(followerId));

    listener.onWatchingSessionJoined(event);

    ArgumentCaptor<NotificationKafkaMessage> messageCaptor =
        ArgumentCaptor.forClass(NotificationKafkaMessage.class);

    then(notificationKafkaProducer).should(times(1)).publish(messageCaptor.capture());

    NotificationKafkaMessage message = messageCaptor.getValue();

    assertThat(message.receiverId()).isEqualTo(followerId);
    assertThat(message.title()).isEqualTo("우디님이 콘텐츠를 시청하기 시작했어요.");
    assertThat(message.content()).isEqualTo("[QA 영화] 시청 중");
    assertThat(message.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(message.notificationType()).isEqualTo(NotificationType.FOLLOWING_USER_ACTIVITY);
  }

  @Test
  @DisplayName("팔로워가 없으면 알림을 발행하지 않는다")
  void onWatchingSessionJoined_noFollowers_doesNotPublish() {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    WatchingSessionJoinedEvent event =
        new WatchingSessionJoinedEvent(watcherId, "우디", contentId, "QA 영화");

    given(followRepository.findActiveFollowerIdsByFolloweeId(watcherId)).willReturn(List.of());

    listener.onWatchingSessionJoined(event);

    then(notificationKafkaProducer).should(never()).publish(any());
  }

  @Test
  @DisplayName("일부 팔로워 알림 Kafka 발행에 실패해도 예외를 전파하지 않는다")
  void onWatchingSessionJoined_kafkaPublishFailure_doesNotPropagate() {
    UUID watcherId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    WatchingSessionJoinedEvent event =
        new WatchingSessionJoinedEvent(watcherId, "우디", contentId, "QA 영화");

    given(followRepository.findActiveFollowerIdsByFolloweeId(watcherId))
        .willReturn(List.of(followerId));
    willThrow(new RuntimeException("kafka publish failed"))
        .given(notificationKafkaProducer)
        .publish(any());

    assertDoesNotThrow(() -> listener.onWatchingSessionJoined(event));

    then(notificationKafkaProducer).should(times(1)).publish(any());
  }

  @Test
  @DisplayName("실시간 시청 시작 이벤트 리스너는 커밋 이후 Kafka 메시지를 발행한다")
  void onWatchingSessionJoined_hasTransactionalEventListenerAfterCommit() throws Exception {
    Method method =
        WatchingSessionJoinedEventListener.class.getMethod(
            "onWatchingSessionJoined", WatchingSessionJoinedEvent.class);

    TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }
}
