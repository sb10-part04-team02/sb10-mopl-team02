package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbClientConfig {

  @Bean
  public RestClient tmdbRestClient(TmdbProperties properties) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(properties.connectTimeout())
            .withReadTimeout(properties.readTimeout());
    RestClient.Builder builder =
        RestClient.builder()
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    return customize(builder, properties).build();
  }

  // 테스트에서 MockRestServiceServer가 바인딩된 builder에 동일한 조립을 적용할 수 있도록 requestFactory와 분리
  static RestClient.Builder customize(RestClient.Builder builder, TmdbProperties properties) {
    return builder
        .baseUrl(properties.baseUrl())
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
        .defaultStatusHandler(
            HttpStatusCode::isError,
            (request, response) -> {
              throw new TmdbApiException(response.getStatusCode().value(), request.getURI());
            });
  }
}
