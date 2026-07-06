package com.team02.mopl.domain.subscription.service;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.subscription.event.SubscriptionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionCreatedEventListener {

  private final NotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onSubscriptionCreated(SubscriptionCreatedEvent event) {
    try {
      notificationService.createNotification(
          new NotificationCreateCommand(
              event.playlistOwnerId(),
              "플레이리스트 구독 알림",
              event.subscriberName() + "님이 [" + event.playlistTitle() + "] 플레이리스트를 구독했습니다.",
              NotificationLevel.INFO,
              NotificationType.PLAYLIST_SUBSCRIBED));
    } catch (RuntimeException e) {
      log.warn(
          "플레이리스트 구독 알림 생성 실패. subscriberId={}, playlistOwnerId={}",
          event.subscriberId(),
          event.playlistOwnerId(),
          e);
    }
  }
}
