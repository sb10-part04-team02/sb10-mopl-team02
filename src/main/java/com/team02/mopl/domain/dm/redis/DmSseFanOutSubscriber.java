package com.team02.mopl.domain.dm.redis;

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
public class DmSseFanOutSubscriber implements MessageListener {

  private static final String DIRECT_MESSAGE_EVENT_NAME = "direct-messages";

  private final ObjectMapper objectMapper;
  private final SseEventService sseEventService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);

    try {
      DmSseFanOutMessage fanOutMessage = objectMapper.readValue(payload, DmSseFanOutMessage.class);
      sseEventService.send(
          fanOutMessage.receiverId(),
          DIRECT_MESSAGE_EVENT_NAME,
          fanOutMessage.eventId(),
          fanOutMessage.data());
    } catch (JsonProcessingException e) {
      log.warn("DM Redis Pub/Sub 메시지 역직렬화 실패. payload={}", payload, e);
    } catch (RuntimeException e) {
      log.warn("DM Redis Pub/Sub 메시지 처리 실패. payload={}", payload, e);
    }
  }
}
