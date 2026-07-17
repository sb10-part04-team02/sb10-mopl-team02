package com.team02.mopl.global.alert;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(DiscordProperties.class)
public class DiscordNotifierConfig {

  @Bean
  public RestClient discordRestClient(DiscordProperties properties) {
    // 웹훅 URL은 전체 URL을 요청마다 지정하므로 baseUrl 없이 타임아웃만 설정
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(properties.connectTimeout())
            .withReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .build();
  }
}
