package com.team02.mopl.domain.dm.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.sse.service.SseEventService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DmSseFanOutPublisher {

  private static final String DIRECT_MESSAGE_EVENT_NAME = "direct-messages";

  private final StringRedisTemplate redisTemplate;
  private final SseEventService sseEventService;
  private final ObjectMapper objectMapper;

  @Value("${app.realtime.redis-fan-out.enabled:true}")
  private boolean redisFanOutEnabled;

  public void publish(UUID receiverId, String eventId, DirectMessageDto data) {
    if (!redisFanOutEnabled) {
      sseEventService.send(receiverId, DIRECT_MESSAGE_EVENT_NAME, eventId, data);
      return;
    }

    DmSseFanOutMessage message = new DmSseFanOutMessage(receiverId, eventId, data);

    try {
      String payload = objectMapper.writeValueAsString(message);
      redisTemplate.convertAndSend(DmSseRedisChannels.DM_SSE_FAN_OUT, payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize DM SSE fan-out message.", e);
    }
  }
}
