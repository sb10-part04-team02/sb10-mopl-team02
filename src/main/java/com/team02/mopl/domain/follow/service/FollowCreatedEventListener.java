package com.team02.mopl.domain.follow.service;

import com.team02.mopl.domain.follow.event.FollowCreatedEvent;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaMessage;
import com.team02.mopl.domain.notification.kafka.NotificationKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowCreatedEventListener {

  private final NotificationKafkaProducer notificationKafkaProducer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFollowCreated(FollowCreatedEvent event) {
    try {
      notificationKafkaProducer.publish(
          new NotificationKafkaMessage(
              event.followeeId(),
              "새 팔로워 알림",
              event.followerName() + "님이 팔로우했습니다.",
              NotificationLevel.INFO,
              NotificationType.USER_FOLLOWED,
              "USER_FOLLOWED:" + event.followeeId() + ":" + event.followId()));
    } catch (RuntimeException e) {
      log.warn(
          "팔로우 알림 Kafka 발행 실패. followerId={}, followeeId={}",
          event.followerId(),
          event.followeeId(),
          e);
    }
  }
}
