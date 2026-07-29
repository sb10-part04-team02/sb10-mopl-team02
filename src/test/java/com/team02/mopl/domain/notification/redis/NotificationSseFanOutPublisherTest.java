package com.team02.mopl.domain.notification.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.sse.service.SseEventService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationSseFanOutPublisherTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private SseEventService sseEventService;

  private NotificationSseFanOutPublisher publisher;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    publisher = new NotificationSseFanOutPublisher(redisTemplate, sseEventService, objectMapper);
  }

  @Test
  @DisplayName("fan-out 활성화 시 Redis로만 발행하고 로컬 SSE 전송은 하지 않는다")
  void publish_fanOutEnabled_publishesToRedisOnly() {
    ReflectionTestUtils.setField(publisher, "redisFanOutEnabled", true);
    NotificationDto dto = notificationDto();

    publisher.publish(dto);

    verify(redisTemplate)
        .convertAndSend(eq(NotificationRedisChannels.NOTIFICATION_SSE_FAN_OUT), anyString());
    verifyNoInteractions(sseEventService);
  }

  @Test
  @DisplayName("fan-out 비활성화 시 로컬 SSE로만 전송하고 Redis는 사용하지 않는다")
  void publish_fanOutDisabled_sendsLocallyOnly() {
    ReflectionTestUtils.setField(publisher, "redisFanOutEnabled", false);
    NotificationDto dto = notificationDto();

    publisher.publish(dto);

    verify(sseEventService).send(dto.receiverId(), "notifications", dto.id().toString(), dto);
    verify(redisTemplate, never()).convertAndSend(anyString(), any(String.class));
  }

  private NotificationDto notificationDto() {
    return new NotificationDto(
        UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "새 알림", "내용", NotificationLevel.INFO);
  }
}
