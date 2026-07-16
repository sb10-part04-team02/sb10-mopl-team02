package com.team02.mopl.global.websocket.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompFanOutSubscriber implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String payload = new String(message.getBody(), StandardCharsets.UTF_8);

    try {
      StompFanOutMessage fanOutMessage = objectMapper.readValue(payload, StompFanOutMessage.class);
      messagingTemplate.convertAndSend(fanOutMessage.destination(), fanOutMessage.payload());
    } catch (JsonProcessingException e) {
      log.warn("STOMP Redis Pub/Sub 메시지 역직렬화 실패. payload={}", payload, e);
    } catch (RuntimeException e) {
      log.warn("STOMP Redis Pub/Sub 메시지 처리 실패. payload={}", payload, e);
    }
  }
}
