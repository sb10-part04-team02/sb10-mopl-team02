package com.team02.mopl.domain.dm.service;

import com.team02.mopl.domain.dm.dto.DmSentEvent;
import com.team02.mopl.domain.dm.redis.DmSseFanOutPublisher;
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
public class DmEventListener {

  private final DmSseFanOutPublisher dmSseFanOutPublisher;
  private final NotificationKafkaProducer notificationKafkaProducer;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDmSent(DmSentEvent event) {
    try {
      notificationKafkaProducer.publish(
          new NotificationKafkaMessage(
              event.receiverUserId(),
              "새 메시지",
              event.dto().sender().name() + "님이 메시지를 보냈습니다.",
              NotificationLevel.INFO,
              NotificationType.DIRECT_MESSAGE_RECEIVED,
              "DIRECT_MESSAGE_RECEIVED:" + event.receiverUserId() + ":" + event.eventId()));
    } catch (RuntimeException e) {
      log.warn(
          "DM 수신 알림 Kafka 발행 실패. receiverUserId={}, eventId={}",
          event.receiverUserId(),
          event.eventId(),
          e);
    }

    try {
      dmSseFanOutPublisher.publish(event.receiverUserId(), event.eventId(), event.dto());
    } catch (RuntimeException e) {
      log.warn(
          "DM 수신 SSE fan-out 발행 실패. receiverUserId={}, eventId={}",
          event.receiverUserId(),
          event.eventId(),
          e);
    }
  }
}
