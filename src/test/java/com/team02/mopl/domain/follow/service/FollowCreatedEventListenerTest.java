package com.team02.mopl.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.team02.mopl.domain.follow.event.FollowCreatedEvent;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import java.lang.reflect.Method;
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
class FollowCreatedEventListenerTest {

  @Mock private NotificationKafkaProducer notificationKafkaProducer;

  @InjectMocks private FollowCreatedEventListener listener;

  @Test
  @DisplayName("팔로우 생성 이벤트를 수신하면 USER_FOLLOWED 알림 Kafka 메시지를 발행한다")
  void onFollowCreated_publishesUserFollowedNotificationKafkaMessage() {
    // given
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    FollowCreatedEvent event = new FollowCreatedEvent(followId, followerId, "팔로워", followeeId);

    // when
    listener.onFollowCreated(event);

    // then
    ArgumentCaptor<NotificationKafkaMessage> messageCaptor =
        ArgumentCaptor.forClass(NotificationKafkaMessage.class);

    then(notificationKafkaProducer).should().publish(messageCaptor.capture());

    NotificationKafkaMessage message = messageCaptor.getValue();

    assertThat(message.receiverId()).isEqualTo(followeeId);
    assertThat(message.title()).isEqualTo("새 팔로워 알림");
    assertThat(message.content()).isEqualTo("팔로워님이 팔로우했습니다.");
    assertThat(message.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(message.notificationType()).isEqualTo(NotificationType.USER_FOLLOWED);
    assertThat(message.dedupKey()).isEqualTo("USER_FOLLOWED:" + followeeId + ":" + followId);
  }

  @Test
  @DisplayName("팔로우 알림 Kafka 발행에 실패해도 예외를 전파하지 않는다")
  void onFollowCreated_kafkaPublishFailure_doesNotThrow() {
    // given
    UUID followId = UUID.randomUUID();
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    FollowCreatedEvent event = new FollowCreatedEvent(followId, followerId, "팔로워", followeeId);

    willThrow(new RuntimeException("kafka publish failed"))
        .given(notificationKafkaProducer)
        .publish(any());

    // when & then
    assertThatCode(() -> listener.onFollowCreated(event)).doesNotThrowAnyException();
    then(notificationKafkaProducer).should().publish(any());
  }

  @Test
  @DisplayName("팔로우 생성 이벤트 리스너는 커밋 이후 Kafka 메시지를 발행한다")
  void onFollowCreated_hasTransactionalEventListenerAfterCommit() throws Exception {
    // given
    Method method =
        FollowCreatedEventListener.class.getMethod("onFollowCreated", FollowCreatedEvent.class);

    // when
    TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

    // then
    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }
}
