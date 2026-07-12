package com.team02.mopl.domain.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaProducer {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public void publish(NotificationKafkaMessage message) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(message);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize notification kafka message.", e);
    }

    kafkaTemplate
        .send(NotificationKafkaTopics.NOTIFICATION_EVENTS, message.receiverId().toString(), payload)
        .whenComplete((result, ex) -> handleSendResult(message, result, ex));
  }

  private void handleSendResult(
      NotificationKafkaMessage message, SendResult<String, String> result, Throwable ex) {
    if (ex != null) {
      log.warn(
          "알림 Kafka 발행 실패. receiverId={}, notificationType={}",
          message.receiverId(),
          message.notificationType(),
          ex);
      return;
    }

    log.debug(
        "알림 Kafka 발행 성공. receiverId={}, topic={}, partition={}, offset={}",
        message.receiverId(),
        result.getRecordMetadata().topic(),
        result.getRecordMetadata().partition(),
        result.getRecordMetadata().offset());
  }
}
