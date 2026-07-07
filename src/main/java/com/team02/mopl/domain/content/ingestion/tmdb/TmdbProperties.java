package com.team02.mopl.domain.content.ingestion.tmdb;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// TMDB API 연동 설정
// - accessToken(v4 Read Access Token)은 미설정 팀원의 부팅을 막지 않도록 blank를 허용
// - 미설정 상태로 수집을 실행하면 TMDB가 401을 응답해 TmdbApiException이 발생
@ConfigurationProperties(prefix = "app.tmdb")
public record TmdbProperties(
    String accessToken,
    String baseUrl,
    String imageBaseUrl,
    String language,
    int pages,
    Duration connectTimeout,
    Duration readTimeout) {

  public TmdbProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("TMDB base-url은 필수입니다.");
    }
    if (imageBaseUrl == null || imageBaseUrl.isBlank()) {
      throw new IllegalStateException("TMDB image-base-url은 필수입니다.");
    }
    if (language == null || language.isBlank()) {
      throw new IllegalStateException("TMDB language는 필수입니다.");
    }
    if (pages < 1) {
      throw new IllegalStateException("TMDB pages는 1 이상이어야 합니다.");
    }
    if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
      throw new IllegalStateException("TMDB connect-timeout은 필수고 0보다 커야 합니다.");
    }
    if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
      throw new IllegalStateException("TMDB read-timeout은 필수고 0보다 커야 합니다.");
    }
  }
}
