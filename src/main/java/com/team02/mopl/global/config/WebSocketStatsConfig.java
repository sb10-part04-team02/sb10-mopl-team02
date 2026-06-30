package com.team02.mopl.global.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;

@Configuration
public class WebSocketStatsConfig {

  @Autowired
  public void configureBrokerStats(WebSocketMessageBrokerStats stats) {
    stats.setLoggingPeriod(60_000); // 1분마다 로그 출력
  }
}
