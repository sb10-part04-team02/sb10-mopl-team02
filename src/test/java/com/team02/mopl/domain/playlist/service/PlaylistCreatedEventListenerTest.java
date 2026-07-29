package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import com.team02.mopl.domain.playlist.event.PlaylistCreatedEvent;
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
class PlaylistCreatedEventListenerTest {

  @Mock private FollowRepository followRepository;

  @Mock private NotificationKafkaProducer notificationKafkaProducer;

  @InjectMocks private PlaylistCreatedEventListener listener;

  @Test
  @DisplayName("플레이리스트 생성 이벤트를 수신하면 팔로워에게 주요 활동 알림 Kafka 메시지를 발행한다")
  void onPlaylistCreated_publishesFollowingUserActivityKafkaMessage() {
    UUID activityId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(activityId, ownerId, "우디", "내 플리", "설명");

    given(followRepository.findActiveFollowerIdsByFolloweeId(ownerId))
        .willReturn(List.of(followerId));

    listener.onPlaylistCreated(event);

    ArgumentCaptor<NotificationKafkaMessage> messageCaptor =
        ArgumentCaptor.forClass(NotificationKafkaMessage.class);

    then(notificationKafkaProducer).should().publish(messageCaptor.capture());

    NotificationKafkaMessage message = messageCaptor.getValue();

    assertThat(message.receiverId()).isEqualTo(followerId);
    assertThat(message.title()).isEqualTo("우디님이 플레이리스트를 만들었어요.");
    assertThat(message.content()).isEqualTo("[내 플리] 설명");
    assertThat(message.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(message.notificationType()).isEqualTo(NotificationType.FOLLOWING_USER_ACTIVITY);
    assertThat(message.dedupKey())
        .isEqualTo("FOLLOWING_USER_ACTIVITY:" + followerId + ":" + activityId);
  }

  @Test
  @DisplayName("팔로워가 없으면 알림을 발행하지 않는다")
  void onPlaylistCreated_noFollowers_doesNotPublish() {
    UUID activityId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(activityId, ownerId, "우디", "내 플리", "설명");

    given(followRepository.findActiveFollowerIdsByFolloweeId(ownerId)).willReturn(List.of());

    listener.onPlaylistCreated(event);

    then(notificationKafkaProducer).should(never()).publish(any());
  }

  @Test
  @DisplayName("팔로워 알림 Kafka 발행에 실패해도 예외를 전파하지 않는다")
  void onPlaylistCreated_kafkaPublishFailure_doesNotStopListener() {
    UUID activityId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();

    PlaylistCreatedEvent event = new PlaylistCreatedEvent(activityId, ownerId, "우디", "내 플리", "설명");

    given(followRepository.findActiveFollowerIdsByFolloweeId(ownerId))
        .willReturn(List.of(followerId));
    willThrow(new RuntimeException("kafka publish failed"))
        .given(notificationKafkaProducer)
        .publish(any());

    assertThatCode(() -> listener.onPlaylistCreated(event)).doesNotThrowAnyException();

    then(notificationKafkaProducer).should().publish(any());
  }

  @Test
  @DisplayName("플레이리스트 생성 이벤트 리스너는 커밋 이후 Kafka 메시지를 발행한다")
  void onPlaylistCreated_hasTransactionalEventListenerAfterCommit() throws Exception {
    Method method =
        PlaylistCreatedEventListener.class.getMethod(
            "onPlaylistCreated", PlaylistCreatedEvent.class);

    TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }
}
