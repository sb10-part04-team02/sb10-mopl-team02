package com.team02.mopl.domain.dm.service;

import com.team02.mopl.domain.dm.dto.DmSentEvent;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.sse.service.SseEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DmEventListener {

  private final SseEventService sseEventService;
  private final NotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDmSent(DmSentEvent event) {
    // DB 쓰기는 NotificationService 자체 트랜잭션에 위임; SSE I/O는 트랜잭션 밖에서 실행
    notificationService.createNotification(
        new NotificationCreateCommand(
            event.receiverUserId(),
            "새 메시지",
            event.dto().sender().name() + "님이 메시지를 보냈습니다.",
            NotificationLevel.INFO,
            NotificationType.DIRECT_MESSAGE_RECEIVED));
    sseEventService.send(event.receiverUserId(), "direct-messages", event.eventId(), event.dto());
  }
}
