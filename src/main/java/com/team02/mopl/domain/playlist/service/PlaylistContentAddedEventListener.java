package com.team02.mopl.domain.playlist.service;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import com.team02.mopl.domain.playlist.event.PlaylistContentAddedEvent;
import com.team02.mopl.domain.subscription.repository.SubscriptionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaylistContentAddedEventListener {

  private final SubscriptionRepository subscriptionRepository;
  private final NotificationKafkaProducer notificationKafkaProducer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlaylistContentAdded(PlaylistContentAddedEvent event) {
    List<UUID> subscriberIds =
        subscriptionRepository.findActiveSubscriberIdsByPlaylistId(event.playlistId());

    for (UUID subscriberId : subscriberIds) {
      try {
        notificationKafkaProducer.publish(
            new NotificationKafkaMessage(
                subscriberId,
                "구독 플레이리스트 콘텐츠 추가 알림",
                "["
                    + event.playlistTitle()
                    + "] 플레이리스트에 "
                    + event.contentTitle()
                    + " 콘텐츠가 추가되었습니다.",
                NotificationLevel.INFO,
                NotificationType.PLAYLIST_CONTENT_ADDED));
      } catch (RuntimeException e) {
        log.warn(
            "플레이리스트 콘텐츠 추가 알림 Kafka 발행 실패. playlistId={}, contentId={}, subscriberId={}",
            event.playlistId(),
            event.contentId(),
            subscriberId,
            e);
      }
    }
  }
}
