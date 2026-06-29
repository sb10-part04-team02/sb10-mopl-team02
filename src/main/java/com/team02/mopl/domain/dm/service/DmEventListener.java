package com.team02.mopl.domain.dm.service;

import com.team02.mopl.domain.dm.dto.DmSentEvent;
import com.team02.mopl.domain.sse.service.SseEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class DmEventListener {

  private final SseEventService sseEventService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onDmSent(DmSentEvent event) {
    sseEventService.send(event.receiverUserId(), "direct-messages", event.eventId(), event.dto());
  }
}
