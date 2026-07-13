package com.team02.mopl.global.config;

import com.team02.mopl.domain.notification.redis.NotificationRedisChannels;
import com.team02.mopl.domain.notification.redis.NotificationSseFanOutSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

  @Bean
  public StringRedisTemplate redisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }

  @Bean
  public RedisMessageListenerContainer redisMessageListenerContainer(
      RedisConnectionFactory connectionFactory,
      NotificationSseFanOutSubscriber notificationSseFanOutSubscriber) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(
        notificationSseFanOutSubscriber,
        new ChannelTopic(NotificationRedisChannels.NOTIFICATION_SSE_FAN_OUT));
    return container;
  }
}
