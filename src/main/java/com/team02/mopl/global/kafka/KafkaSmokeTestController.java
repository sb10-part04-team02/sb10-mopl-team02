package com.team02.mopl.global.kafka;

import java.time.Instant;
import java.util.concurrent.CompletionException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
public class KafkaSmokeTestController {

  private final KafkaTemplate<String, String> kafkaTemplate;

  public KafkaSmokeTestController(KafkaTemplate<String, String> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  @PostMapping("/api/dev/kafka/smoke")
  public ResponseEntity<Void> publishSmokeMessage() {
    try {
      SendResult<String, String> result =
          kafkaTemplate
              .send(
                  KafkaSmokeTestTopics.LOCAL_TEST,
                  "local-smoke",
                  "Kafka smoke test: " + Instant.now())
              .join();

      return ResponseEntity.noContent()
          .header("X-Kafka-Topic", result.getRecordMetadata().topic())
          .header("X-Kafka-Partition", String.valueOf(result.getRecordMetadata().partition()))
          .header("X-Kafka-Offset", String.valueOf(result.getRecordMetadata().offset()))
          .build();
    } catch (CompletionException e) {
      throw new IllegalStateException("Kafka smoke message send failed.", e);
    }
  }
}
