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
      @Value("${app.kafka.consumer.retry.max-attempts:3}") long maxAttempts) {
    return new DefaultErrorHandler(new FixedBackOff(retryInterval.toMillis(), maxAttempts));
  }
}
