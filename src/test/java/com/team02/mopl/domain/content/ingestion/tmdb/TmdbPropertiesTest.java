package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
class TmdbPropertiesTest {

  private static final String baseUrl = "https://api.themoviedb.org/3";
  private static final String imageBaseUrl = "https://image.tmdb.org/t/p/w500";
  private static final String language = "ko-KR";
  private static final Duration timeout = Duration.ofSeconds(3);

  @Test
  @DisplayName("유효한 값이면 생성에 성공하고, accessToken은 blank여도 허용된다")
  void success_shouldAllowBlankAccessToken() {
    assertDoesNotThrow(
        () -> new TmdbProperties("", baseUrl, imageBaseUrl, language, 3, timeout, timeout));
  }

  // 실패 케이스 데이터
  private Stream<Arguments> provideInvalidProperties() {
    return Stream.of(
        Arguments.of(null, imageBaseUrl, language, 3, timeout, timeout, "baseUrl 누락"),
        Arguments.of(" ", imageBaseUrl, language, 3, timeout, timeout, "baseUrl blank"),
        Arguments.of(baseUrl, null, language, 3, timeout, timeout, "imageBaseUrl 누락"),
        Arguments.of(baseUrl, imageBaseUrl, null, 3, timeout, timeout, "language 누락"),
        Arguments.of(baseUrl, imageBaseUrl, language, 0, timeout, timeout, "pages 0"),
        Arguments.of(baseUrl, imageBaseUrl, language, -1, timeout, timeout, "pages 음수"),
        Arguments.of(baseUrl, imageBaseUrl, language, 3, null, timeout, "connectTimeout 누락"),
        Arguments.of(
            baseUrl, imageBaseUrl, language, 3, Duration.ZERO, timeout, "connectTimeout 0"),
        Arguments.of(baseUrl, imageBaseUrl, language, 3, timeout, null, "readTimeout 누락"),
        Arguments.of(
            baseUrl, imageBaseUrl, language, 3, timeout, Duration.ofSeconds(-1), "readTimeout 음수"));
  }

  @ParameterizedTest
  @MethodSource("provideInvalidProperties")
  @DisplayName("유효하지 않은 설정 값은 IllegalStateException을 발생시킨다")
  void fail_shouldThrowIllegalStateException_whenInvalid(
      String baseUrl,
      String imageBaseUrl,
      String language,
      int pages,
      Duration connectTimeout,
      Duration readTimeout,
      String description) {
    assertThrows(
        IllegalStateException.class,
        () ->
            new TmdbProperties(
                "token", baseUrl, imageBaseUrl, language, pages, connectTimeout, readTimeout));
  }
}
