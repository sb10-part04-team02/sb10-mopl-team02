package com.team02.mopl.global.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("dev")
@Component
public class KafkaSmokeTestListener {

  @KafkaListener(
      topics = KafkaSmokeTestTopics.LOCAL_TEST,
      groupId = "${spring.kafka.consumer.group-id}")
  public void listen(String message) {
    log.info("Kafka smoke message received: {}", message);
  }
}
