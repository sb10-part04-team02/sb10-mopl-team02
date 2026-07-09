package com.team02.mopl.global.kafka;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequiredArgsConstructor
public class KafkaSmokeTestController {

  private static final String TOPIC = "mopl.local.test";

  private final KafkaTemplate<String, String> kafkaTemplate;

  @PostMapping("/api/dev/kafka/smoke")
  public ResponseEntity<Void> publishSmokeMessage() {
    kafkaTemplate.send(TOPIC, "local-smoke", "Kafka smoke test: " + Instant.now());
    return ResponseEntity.noContent().build();
  }
}
