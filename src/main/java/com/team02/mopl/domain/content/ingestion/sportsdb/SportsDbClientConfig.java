package com.team02.mopl.domain.content.ingestion.sportsdb;

import com.team02.mopl.domain.content.ingestion.exception.SportsDbApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

@Slf4j
@Configuration
@EnableConfigurationProperties(SportsDbProperties.class)
public class SportsDbClientConfig {

  @Bean
  public RestClient sportsDbRestClient(SportsDbProperties properties) {
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
  static RestClient.Builder customize(RestClient.Builder builder, SportsDbProperties properties) {
    return builder
        // v1 인증 방식: API 키가 URL 경로 세그먼트로 들어간다 (헤더 인증 아님)
        .baseUrl(properties.baseUrl() + "/" + properties.apiKey())
        .defaultStatusHandler( // 예외처리
            HttpStatusCode::isError,
            (request, response) -> {
              int statusCode = response.getStatusCode().value();
              // 요청 URI는 클라이언트 응답(details)에 노출하지 않고 로그로만 남긴다
              // URI 경로에 API 키가 포함되므로 로그에도 마스킹해서 남긴다
              log.warn(
                  "SportsDB API 오류 응답: status={}, uri={}",
                  statusCode,
                  maskApiKey(String.valueOf(request.getURI()), properties.apiKey()));
              throw new SportsDbApiException(statusCode);
            });
  }

  // URI 문자열의 API 키 경로 세그먼트를 가린다: "/json/{key}/" -> "/json/***/"
  static String maskApiKey(String uri, String apiKey) {
    return uri.replace("/" + apiKey + "/", "/***/");
  }
}
