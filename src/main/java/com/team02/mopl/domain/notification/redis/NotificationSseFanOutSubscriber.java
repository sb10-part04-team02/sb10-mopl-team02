package com.team02.mopl.domain.notification.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.sse.service.SseEventService;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationSseFanOutSubscriber implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SseEventService sseEventService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);

    try {
      NotificationSseFanOutMessage fanOutMessage =
          objectMapper.readValue(payload, NotificationSseFanOutMessage.class);

      sseEventService.send(
          fanOutMessage.receiverId(),
          fanOutMessage.eventName(),
          fanOutMessage.eventId(),
          fanOutMessage.data());
    } catch (JsonProcessingException e) {
      log.warn("알림 Redis Pub/Sub 메시지 역직렬화 실패. payload={}", payload, e);
    } catch (RuntimeException e) {
      log.warn("알림 Redis Pub/Sub 메시지 처리 실패. payload={}", payload, e);
    }
  }
}
