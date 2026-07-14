package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@ExtendWith(MockitoExtension.class)
class PlaylistContentAddedEventListenerTest {

  @Mock private SubscriptionRepository subscriptionRepository;

  @Mock private NotificationKafkaProducer notificationKafkaProducer;

  @InjectMocks private PlaylistContentAddedEventListener listener;

  @Test
  @DisplayName("플레이리스트 콘텐츠 추가 이벤트를 수신하면 구독자에게 알림 Kafka 메시지를 발행한다")
  void onPlaylistContentAdded_publishesKafkaMessageForSubscribers() {
    UUID playlistContentId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();

    PlaylistContentAddedEvent event =
        new PlaylistContentAddedEvent(playlistContentId, playlistId, "내 플리", contentId, "콘텐츠 제목");

    given(subscriptionRepository.findActiveSubscriberIdsByPlaylistId(playlistId))
        .willReturn(List.of(subscriberId));

    listener.onPlaylistContentAdded(event);

    ArgumentCaptor<NotificationKafkaMessage> messageCaptor =
        ArgumentCaptor.forClass(NotificationKafkaMessage.class);

    then(notificationKafkaProducer).should().publish(messageCaptor.capture());

    NotificationKafkaMessage message = messageCaptor.getValue();

    assertThat(message.receiverId()).isEqualTo(subscriberId);
    assertThat(message.title()).isEqualTo("구독 플레이리스트 콘텐츠 추가 알림");
    assertThat(message.content()).isEqualTo("[내 플리] 플레이리스트에 콘텐츠 제목 콘텐츠가 추가되었습니다.");
    assertThat(message.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(message.notificationType()).isEqualTo(NotificationType.PLAYLIST_CONTENT_ADDED);
    assertThat(message.dedupKey())
        .isEqualTo("PLAYLIST_CONTENT_ADDED:" + subscriberId + ":" + playlistContentId);
  }

  @Test
  @DisplayName("구독자가 없으면 알림을 발행하지 않는다")
  void onPlaylistContentAdded_noSubscribers_doesNotPublish() {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    PlaylistContentAddedEvent event =
        new PlaylistContentAddedEvent(UUID.randomUUID(), playlistId, "내 플리", contentId, "콘텐츠 제목");

    given(subscriptionRepository.findActiveSubscriberIdsByPlaylistId(playlistId))
        .willReturn(List.of());

    listener.onPlaylistContentAdded(event);

    then(notificationKafkaProducer).should(never()).publish(any());
  }

  @Test
  @DisplayName("일부 구독자 알림 Kafka 발행에 실패해도 예외를 전파하지 않는다")
  void onPlaylistContentAdded_kafkaPublishFailure_doesNotThrow() {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();

    PlaylistContentAddedEvent event =
        new PlaylistContentAddedEvent(UUID.randomUUID(), playlistId, "내 플리", contentId, "콘텐츠 제목");

    given(subscriptionRepository.findActiveSubscriberIdsByPlaylistId(playlistId))
        .willReturn(List.of(subscriberId));
    willThrow(new RuntimeException("kafka publish failed"))
        .given(notificationKafkaProducer)
        .publish(any());

    assertThatCode(() -> listener.onPlaylistContentAdded(event)).doesNotThrowAnyException();

    then(notificationKafkaProducer).should().publish(any());
  }

  @Test
  @DisplayName("플레이리스트 콘텐츠 추가 이벤트 리스너는 커밋 이후 Kafka 메시지를 발행한다")
  void onPlaylistContentAdded_hasTransactionalEventListenerAfterCommit() throws Exception {
    Method method =
        PlaylistContentAddedEventListener.class.getMethod(
            "onPlaylistContentAdded", PlaylistContentAddedEvent.class);

    TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

    assertThat(annotation).isNotNull();
    assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
  }
}
