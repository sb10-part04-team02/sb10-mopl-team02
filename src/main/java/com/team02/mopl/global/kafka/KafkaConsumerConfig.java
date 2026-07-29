package com.team02.mopl.global.kafka;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

  @Bean
  public DefaultErrorHandler kafkaDefaultErrorHandler(
      @Value("${app.kafka.consumer.retry.interval:1s}") Duration retryInterval,
      @Value("${app.kafka.consumer.retry.max-retries:3}") long maxRetries) {
    // FixedBackOff의 두 번째 인자는 최초 처리 실패 이후 재시도 횟수다.
    return new DefaultErrorHandler(new FixedBackOff(retryInterval.toMillis(), maxRetries));
  }
}
