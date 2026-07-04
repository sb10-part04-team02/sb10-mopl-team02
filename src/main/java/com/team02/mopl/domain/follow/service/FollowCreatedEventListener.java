package com.team02.mopl.domain.follow.service;

import com.team02.mopl.domain.follow.event.FollowCreatedEvent;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowCreatedEventListener {

  private final NotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFollowCreated(FollowCreatedEvent event) {
    try {
      notificationService.createNotification(
          new NotificationCreateCommand(
              event.followeeId(),
              "새 팔로워 알림",
              event.followerName() + "님이 팔로우했습니다.",
              NotificationLevel.INFO,
              NotificationType.USER_FOLLOWED));
    } catch (RuntimeException e) {
      log.warn(
          "팔로우 알림 생성 실패. followerId={}, followeeId={}", event.followerId(), event.followeeId(), e);
    }
  }
}
