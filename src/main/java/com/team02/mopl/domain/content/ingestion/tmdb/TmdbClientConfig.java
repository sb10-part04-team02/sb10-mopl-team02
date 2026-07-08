package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbClientConfig {

  @Bean
  public RestClient tmdbRestClient(TmdbProperties properties) {
    // 타임아웃 설정
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(properties.connectTimeout())
            .withReadTimeout(properties.readTimeout());
    RestClient.Builder builder =
        RestClient.builder()
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings));
    return customize(builder, properties).build(); // RestClient 인스턴스 생성 후 반환
  }

  // 테스트에서 MockRestServiceServer가 바인딩된 builder에 동일한 조립을 적용할 수 있도록 requestFactory와 분리
  static RestClient.Builder customize(RestClient.Builder builder, TmdbProperties properties) {
    return builder
        .baseUrl(properties.baseUrl()) // 공통 URL
        // 모든 요청에 인증 헤더를 자동으로 추가
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
        .defaultStatusHandler( // 예외처리
            HttpStatusCode::isError,
            (request, response) -> {
              int statusCode = response.getStatusCode().value();
              // 요청 URI는 클라이언트 응답(details)에 노출하지 않고 로그로만 남긴다
              log.warn("TMDB API 오류 응답: status={}, uri={}", statusCode, request.getURI());
              throw new TmdbApiException(statusCode);
            });
  }
}
