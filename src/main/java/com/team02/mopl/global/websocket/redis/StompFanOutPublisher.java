package com.team02.mopl.global.websocket.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompFanOutPublisher {

  private final StringRedisTemplate redisTemplate;
  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectMapper objectMapper;

  @Value("${app.realtime.redis-fan-out.enabled:true}")
  private boolean redisFanOutEnabled;

  public void publish(String destination, Object payload) {
    if (!redisFanOutEnabled) {
      messagingTemplate.convertAndSend(destination, payload);
      return;
    }

    JsonNode payloadNode = objectMapper.valueToTree(payload);
    StompFanOutMessage message = new StompFanOutMessage(destination, payloadNode);

    try {
      String json = objectMapper.writeValueAsString(message);
      redisTemplate.convertAndSend(StompRedisChannels.STOMP_FANOUT, json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize STOMP fan-out message.", e);
    }
  }
}
