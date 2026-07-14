package com.team02.mopl.domain.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import com.team02.mopl.domain.subscription.event.SubscriptionCreatedEvent;
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
class SubscriptionCreatedEventListenerTest {

  @Mock private NotificationKafkaProducer notificationKafkaProducer;

  @InjectMocks private SubscriptionCreatedEventListener listener;

  @Test
  @DisplayName("구독 생성 이벤트를 수신하면 PLAYLIST_SUBSCRIBED 알림 Kafka 메시지를 발행한다")
  void onSubscriptionCreated_publishesPlaylistSubscribedKafkaMessage() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistOwnerId = UUID.randomUUID();

    SubscriptionCreatedEvent event =
        new SubscriptionCreatedEvent(subscriberId, "구독자", playlistOwnerId, "내 플리");

    listener.onSubscriptionCreated(event);

    ArgumentCaptor<NotificationKafkaMessage> messageCaptor =
        ArgumentCaptor.forClass(NotificationKafkaMessage.class);

    then(notificationKafkaProducer).should().publish(messageCaptor.capture());

    NotificationKafkaMessage message = messageCaptor.getValue();

    assertThat(message.receiverId()).isEqualTo(playlistOwnerId);
    assertThat(message.title()).isEqualTo("플레이리스트 구독 알림");
    assertThat(message.content()).isEqualTo("구독자님이 [내 플리] 플레이리스트를 구독했습니다.");
    assertThat(message.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(message.notificationType()).isEqualTo(NotificationType.PLAYLIST_SUBSCRIBED);
  }

  @Test
  @DisplayName("구독 알림 Kafka 발행에 실패해도 예외를 전파하지 않는다")
  void onSubscriptionCreated_kafkaPublishFailure_doesNotThrow() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistOwnerId = UUID.randomUUID();

    SubscriptionCreatedEvent event =
        new SubscriptionCreatedEvent(subscriberId, "구독자", playlistOwnerId, "내 플리");

    willThrow(new RuntimeException("kafka publish failed"))
        .given(notificationKafkaProducer)
        .publish(any());

    assertThatCode(() -> listener.onSubscriptionCreated(event)).doesNotThrowAnyException();
    then(notificationKafkaProducer).should().publish(any());
  }

  @Test
  @DisplayName("구독 생성 이벤트 리스너는 커밋 이후 Kafka 메시지를 발행한다")
  void onSubscriptionCreated_hasTransactionalEventListenerAfterCommit() throws Exception {
    Method method =
        SubscriptionCreatedEventListener.class.getMethod(
            "onSubscriptionCreated", SubscriptionCreatedEvent.class);

    TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }
}
