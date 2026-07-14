package com.team02.mopl.domain.watching.service;

import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import com.team02.mopl.domain.watching.event.WatchingSessionJoinedEvent;
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
public class WatchingSessionJoinedEventListener {

  private final FollowRepository followRepository;
  private final NotificationKafkaProducer notificationKafkaProducer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onWatchingSessionJoined(WatchingSessionJoinedEvent event) {
    List<UUID> followerIds = followRepository.findActiveFollowerIdsByFolloweeId(event.watcherId());

    for (UUID followerId : followerIds) {
      try {
        notificationKafkaProducer.publish(
            new NotificationKafkaMessage(
                followerId,
                event.watcherName() + "님이 콘텐츠를 시청하기 시작했어요.",
                "[" + event.contentTitle() + "] 시청 중",
                NotificationLevel.INFO,
                NotificationType.FOLLOWING_USER_ACTIVITY));
      } catch (RuntimeException e) {
        log.warn(
            "watching.notification_kafka_publish_failed watcherId={} followerId={} contentId={}",
            event.watcherId(),
            followerId,
            event.contentId(),
            e);
      }
    }
  }
}
