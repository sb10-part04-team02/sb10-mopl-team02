package com.team02.mopl.domain.dm.redis;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.sse.service.SseEventService;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

@ExtendWith(MockitoExtension.class)
class DmSseFanOutSubscriberTest {

  @Mock private SseEventService sseEventService;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private DmSseFanOutSubscriber subscriber;

  @BeforeEach
  void setUp() {
    subscriber = new DmSseFanOutSubscriber(objectMapper, sseEventService);
  }

  @Test
  @DisplayName("Redis 메시지를 역직렬화해 direct-messages 이벤트로 로컬 SSE 전송한다")
  void onMessage_validPayload_sendsSseLocally() throws Exception {
    UUID receiverId = UUID.randomUUID();
    String eventId = UUID.randomUUID().toString();
    DirectMessageDto dto = directMessageDto(receiverId);
    DmSseFanOutMessage fanOutMessage = new DmSseFanOutMessage(receiverId, eventId, dto);
    String json = objectMapper.writeValueAsString(fanOutMessage);
    Message message = new DefaultMessage(new byte[0], json.getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    verify(sseEventService).send(receiverId, "direct-messages", eventId, dto);
  }

  @Test
  @DisplayName("역직렬화에 실패하면 예외를 전파하지 않고 로컬 전송도 하지 않는다")
  void onMessage_invalidPayload_doesNotThrowAndDoesNotSend() {
    Message message = mock(Message.class);
    when(message.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    verifyNoInteractions(sseEventService);
  }

  private DirectMessageDto directMessageDto(UUID receiverId) {
    UUID senderId = UUID.randomUUID();
    return new DirectMessageDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now(),
        new UserSummary(senderId, "발신자", null),
        new UserSummary(receiverId, "수신자", null),
        "안녕하세요");
  }
}
