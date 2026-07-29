package com.team02.mopl.global.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("dev")
@Component
public class KafkaSmokeTestListener {

  // 기본은 자동 기동 끔(false). 브로커 미기동 시 재접속 WARN 스팸 방지.
  // 스모크 테스트 시에만 app.kafka.smoke-test.enabled=true 로 켠다.
  @KafkaListener(
      topics = KafkaSmokeTestTopics.LOCAL_TEST,
      groupId = "${spring.kafka.consumer.group-id}",
      autoStartup = "${app.kafka.smoke-test.enabled:false}")
  public void listen(String message) {
    log.info("Kafka smoke message received: {}", message);
  }
}
