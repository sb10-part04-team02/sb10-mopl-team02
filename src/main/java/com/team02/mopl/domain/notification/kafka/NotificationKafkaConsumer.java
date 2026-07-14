package com.team02.mopl.domain.notification.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

  private final ObjectMapper objectMapper;
  private final NotificationService notificationService;

  @KafkaListener(
      topics = NotificationKafkaTopics.NOTIFICATION_EVENTS,
      groupId = "${spring.kafka.consumer.group-id}")
  public void consume(String payload) {
    try {
      NotificationKafkaMessage message =
          objectMapper.readValue(payload, NotificationKafkaMessage.class);

      notificationService.createNotification(
          new NotificationCreateCommand(
              message.receiverId(),
              message.title(),
              message.content(),
              message.level(),
              message.notificationType(),
              message.dedupKey()));
    } catch (JsonProcessingException e) {
      log.warn("알림 Kafka 메시지 역직렬화 실패. payload={}", payload, e);
    }
  }
}
