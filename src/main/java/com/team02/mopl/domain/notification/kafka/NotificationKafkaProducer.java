package com.team02.mopl.domain.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationKafkaProducer {

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public void publish(NotificationKafkaMessage message) {
    try {
      String payload = objectMapper.writeValueAsString(message);

      kafkaTemplate
          .send(
              NotificationKafkaTopics.NOTIFICATION_EVENTS, message.receiverId().toString(), payload)
          .join();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize notification kafka message.", e);
    }
  }
}
