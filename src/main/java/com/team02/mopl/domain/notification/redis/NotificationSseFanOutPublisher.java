package com.team02.mopl.domain.notification.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.sse.service.SseEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationSseFanOutPublisher {

  private static final String NOTIFICATION_EVENT_NAME = "notifications";

  private final StringRedisTemplate redisTemplate;
  private final SseEventService sseEventService;
  private final ObjectMapper objectMapper;

  @Value("${app.realtime.redis-fan-out.enabled:true}")
  private boolean redisFanOutEnabled;

  public void publish(NotificationDto notificationDto) {
    if (!redisFanOutEnabled) {
      sseEventService.send(
          notificationDto.receiverId(),
          NOTIFICATION_EVENT_NAME,
          notificationDto.id().toString(),
          notificationDto);
      return;
    }

    NotificationSseFanOutMessage message =
        new NotificationSseFanOutMessage(
            notificationDto.receiverId(),
            NOTIFICATION_EVENT_NAME,
            notificationDto.id().toString(),
            notificationDto);

    try {
      String payload = objectMapper.writeValueAsString(message);
      redisTemplate.convertAndSend(NotificationRedisChannels.NOTIFICATION_SSE_FAN_OUT, payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize notification SSE fan-out message.", e);
    }
  }
}
