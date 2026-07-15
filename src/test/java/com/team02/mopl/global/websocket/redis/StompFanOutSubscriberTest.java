package com.team02.mopl.global.websocket.redis;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class StompFanOutSubscriberTest {

  @Mock private SimpMessagingTemplate messagingTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private StompFanOutSubscriber subscriber;

  @BeforeEach
  void setUp() {
    subscriber = new StompFanOutSubscriber(objectMapper, messagingTemplate);
  }

  @Test
  @DisplayName("Redis 메시지를 역직렬화해 destination으로 로컬 브로커에 전송한다")
  void onMessage_validPayload_broadcastsToLocalBroker() throws Exception {
    String destination = "/sub/contents/content-1/chat";
    StompFanOutMessage fanOutMessage =
        new StompFanOutMessage(destination, TextNode.valueOf("안녕하세요"));
    String json = objectMapper.writeValueAsString(fanOutMessage);
    Message message = new DefaultMessage(new byte[0], json.getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    verify(messagingTemplate).convertAndSend(eq(destination), eq(TextNode.valueOf("안녕하세요")));
  }

  @Test
  @DisplayName("역직렬화에 실패하면 예외를 전파하지 않고 로컬 전송도 하지 않는다")
  void onMessage_invalidPayload_doesNotThrowAndDoesNotSend() {
    Message message = mock(Message.class);
    when(message.getBody()).thenReturn("not-json".getBytes(StandardCharsets.UTF_8));

    subscriber.onMessage(message, null);

    verifyNoInteractions(messagingTemplate);
  }
}
