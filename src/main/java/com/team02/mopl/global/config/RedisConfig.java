package com.team02.mopl.global.config;

import com.team02.mopl.domain.notification.redis.NotificationRedisChannels;
import com.team02.mopl.domain.notification.redis.NotificationSseFanOutSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class RedisConfig {

  @Bean
  public StringRedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }

  @Bean
  public TaskExecutor redisMessageListenerTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("redis-listener-");
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.initialize();
    return executor;
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      NotificationSseFanOutSubscriber notificationSseFanOutSubscriber,
      TaskExecutor redisMessageListenerTaskExecutor) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.setTaskExecutor(redisMessageListenerTaskExecutor);
    container.addMessageListener(
        notificationSseFanOutSubscriber,
        new ChannelTopic(NotificationRedisChannels.NOTIFICATION_SSE_FAN_OUT));
    return container;
  }
}
