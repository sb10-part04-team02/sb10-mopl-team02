package com.team02.mopl.global.websocket.redis;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StompFanOutPublisherTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private SimpMessagingTemplate messagingTemplate;

  private StompFanOutPublisher publisher;

  @BeforeEach
  void setUp() {
    publisher = new StompFanOutPublisher(redisTemplate, messagingTemplate, new ObjectMapper());
  }

  @Test
  @DisplayName("fan-out 활성화 시 Redis로만 발행하고 로컬 브로커로는 전송하지 않는다")
  void publish_fanOutEnabled_publishesToRedisOnly() {
    ReflectionTestUtils.setField(publisher, "redisFanOutEnabled", true);
    String destination = "/sub/contents/content-1/chat";
    Map<String, String> payload = Map.of("text", "안녕하세요");

    publisher.publish(destination, payload);

    verify(redisTemplate).convertAndSend(eq(StompRedisChannels.STOMP_FANOUT), anyString());
    verifyNoInteractions(messagingTemplate);
  }

  @Test
  @DisplayName("fan-out 비활성화 시 로컬 브로커로만 전송하고 Redis는 사용하지 않는다")
  void publish_fanOutDisabled_sendsLocallyOnly() {
    ReflectionTestUtils.setField(publisher, "redisFanOutEnabled", false);
    String destination = "/sub/contents/content-1/chat";
    Map<String, String> payload = Map.of("text", "안녕하세요");

    publisher.publish(destination, payload);

    verify(messagingTemplate).convertAndSend(destination, payload);
    verify(redisTemplate, never()).convertAndSend(anyString(), any(String.class));
  }
}
