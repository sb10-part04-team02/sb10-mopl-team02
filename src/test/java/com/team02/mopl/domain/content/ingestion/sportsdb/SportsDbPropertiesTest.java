package com.team02.mopl.domain.content.ingestion.sportsdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.team02.mopl.domain.content.ingestion.sportsdb.SportsDbProperties.League;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(Lifecycle.PER_CLASS)
class SportsDbPropertiesTest {

  private static final String apiKey = "123";
  private static final String baseUrl = "https://www.thesportsdb.com/api/v1/json";
  private static final Duration timeout = Duration.ofSeconds(3);
  private static final List<League> leagues = List.of(new League("4328", "2025-2026"));

  @Test
  @DisplayName("유효한 값이면 생성에 성공한다")
  void success_shouldCreateProperties() {
    assertDoesNotThrow(() -> new SportsDbProperties(apiKey, baseUrl, timeout, timeout, leagues));
  }

  @Test
  @DisplayName("leagues가 null이면 빈 리스트로 치환한다 (부팅은 막지 않고 수집 시 0건)")
  void success_shouldReplaceNullLeaguesWithEmptyList() {
    // given - leagues 미설정
    // when
    SportsDbProperties properties = new SportsDbProperties(apiKey, baseUrl, timeout, timeout, null);

    // then
    assertThat(properties.leagues()).isEmpty();
  }

  // 실패 케이스 데이터
  private Stream<Arguments> provideInvalidProperties() {
    return Stream.of(
        Arguments.of(null, baseUrl, timeout, timeout, "apiKey 누락"),
        Arguments.of(" ", baseUrl, timeout, timeout, "apiKey blank"),
        Arguments.of(apiKey, null, timeout, timeout, "baseUrl 누락"),
        Arguments.of(apiKey, " ", timeout, timeout, "baseUrl blank"),
        Arguments.of(apiKey, baseUrl, null, timeout, "connectTimeout 누락"),
        Arguments.of(apiKey, baseUrl, Duration.ZERO, timeout, "connectTimeout 0"),
        Arguments.of(apiKey, baseUrl, timeout, null, "readTimeout 누락"),
        Arguments.of(apiKey, baseUrl, timeout, Duration.ofSeconds(-1), "readTimeout 음수"));
  }

  @ParameterizedTest
  @MethodSource("provideInvalidProperties")
  @DisplayName("유효하지 않은 설정 값은 IllegalStateException을 발생시킨다")
  void fail_shouldThrowIllegalStateException_whenInvalid(
      String apiKey,
      String baseUrl,
      Duration connectTimeout,
      Duration readTimeout,
      String description) {
    assertThrows(
        IllegalStateException.class,
        () -> new SportsDbProperties(apiKey, baseUrl, connectTimeout, readTimeout, leagues));
  }

  @Test
  @DisplayName("League의 id/season이 blank면 IllegalStateException을 발생시킨다")
  void fail_shouldThrowIllegalStateException_whenLeagueFieldBlank() {
    assertThrows(IllegalStateException.class, () -> new League(" ", "2025-2026"));
    assertThrows(IllegalStateException.class, () -> new League("4328", null));
  }
}
