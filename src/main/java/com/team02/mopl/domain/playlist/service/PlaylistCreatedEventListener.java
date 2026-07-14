package com.team02.mopl.domain.playlist.service;

import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import com.team02.mopl.domain.playlist.event.PlaylistCreatedEvent;
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
public class PlaylistCreatedEventListener {

  private final FollowRepository followRepository;
  private final NotificationKafkaProducer notificationKafkaProducer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlaylistCreated(PlaylistCreatedEvent event) {
    List<UUID> followerIds = followRepository.findActiveFollowerIdsByFolloweeId(event.ownerId());

    for (UUID followerId : followerIds) {
      try {
        notificationKafkaProducer.publish(
            new NotificationKafkaMessage(
                followerId,
                event.ownerName() + "님이 플레이리스트를 만들었어요.",
                "[" + event.playlistTitle() + "] " + event.playlistDescription(),
                NotificationLevel.INFO,
                NotificationType.FOLLOWING_USER_ACTIVITY,
                "FOLLOWING_USER_ACTIVITY:" + followerId + ":" + event.activityId()));
      } catch (RuntimeException e) {
        log.warn(
            "플레이리스트 생성 알림 Kafka 발행 실패. ownerId={}, followerId={}", event.ownerId(), followerId, e);
      }
    }
  }
}
