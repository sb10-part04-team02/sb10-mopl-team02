package com.team02.mopl.global.kafka;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
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

  private final KafkaTemplate<String, String> kafkaTemplate;

  @PostMapping("/api/dev/kafka/smoke")
  public ResponseEntity<Void> publishSmokeMessage() {
    try {
      kafkaTemplate
          .send(
              KafkaSmokeTestTopics.LOCAL_TEST, "local-smoke", "Kafka smoke test: " + Instant.now())
          .get();

      return ResponseEntity.noContent().build();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Kafka smoke message send was interrupted.", e);
    } catch (ExecutionException e) {
      throw new IllegalStateException("Kafka smoke message send failed.", e);
    }
  }
}
