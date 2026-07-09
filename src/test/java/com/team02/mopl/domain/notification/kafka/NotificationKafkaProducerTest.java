package com.team02.mopl.domain.notification.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaProducerTest {

  @Mock private KafkaTemplate<String, String> kafkaTemplate;
  @Mock private ObjectMapper objectMapper;
  @Mock private SendResult<String, String> sendResult;

  @Test
  @DisplayName("알림 Kafka 메시지를 JSON으로 직렬화해 토픽에 발행한다")
  void publish_success() throws Exception {
    // given
    NotificationKafkaProducer producer = new NotificationKafkaProducer(kafkaTemplate, objectMapper);

    UUID receiverId = UUID.randomUUID();
    NotificationKafkaMessage message =
        new NotificationKafkaMessage(
            receiverId,
            "새 팔로워 알림",
            "팔로워님이 팔로우했습니다.",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED);

    String payload = "{\"receiverId\":\"" + receiverId + "\"}";

    given(objectMapper.writeValueAsString(message)).willReturn(payload);
    given(
            kafkaTemplate.send(
                NotificationKafkaTopics.NOTIFICATION_EVENTS, receiverId.toString(), payload))
        .willReturn(CompletableFuture.completedFuture(sendResult));

    // when
    producer.publish(message);

    // then
    then(objectMapper).should().writeValueAsString(message);
    then(kafkaTemplate)
        .should()
        .send(
            eq(NotificationKafkaTopics.NOTIFICATION_EVENTS),
            eq(receiverId.toString()),
            eq(payload));
  }

  @Test
  @DisplayName("알림 Kafka 메시지 직렬화에 실패하면 예외를 던진다")
  void publish_fail_whenSerializationFails() throws Exception {
    // given
    NotificationKafkaProducer producer = new NotificationKafkaProducer(kafkaTemplate, objectMapper);

    NotificationKafkaMessage message =
        new NotificationKafkaMessage(
            UUID.randomUUID(),
            "새 팔로워 알림",
            "팔로워님이 팔로우했습니다.",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED);

    given(objectMapper.writeValueAsString(message))
        .willThrow(new JsonProcessingException("serialize failed") {});

    // when & then
    assertThatThrownBy(() -> producer.publish(message))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Failed to serialize notification kafka message.");
  }
}
