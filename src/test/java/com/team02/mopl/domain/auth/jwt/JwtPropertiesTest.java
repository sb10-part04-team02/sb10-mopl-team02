package com.team02.mopl.domain.auth.jwt;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

@TestInstance(Lifecycle.PER_CLASS)
@ExtendWith(MockitoExtension.class)
class JwtPropertiesTest {

  private static final String validKey = "12345678901234567890123456789012"; // 32 바이트
  private static final Duration accessTokenExpiration = Duration.ofMinutes(10);
  private static final Duration refreshTokenExpiration = Duration.ofDays(7);

  private Stream<Arguments> provideInvalidTokenExpiration() {
    return Stream.of(
        Arguments.of(null, refreshTokenExpiration, "AccessTokenExpiration 누락"),
        Arguments.of(Duration.ofDays(-1), refreshTokenExpiration, "AccessTokenExpiration 음수"),
        Arguments.of(Duration.ZERO, refreshTokenExpiration, "AccessTokenExpiration 0"),
        Arguments.of(accessTokenExpiration, null, "RefreshTokenExpiration 누락"),
        Arguments.of(accessTokenExpiration, Duration.ofDays(-1), "RefreshTokenExpiration 음수"),
        Arguments.of(accessTokenExpiration, Duration.ZERO, "RefreshTokenExpiration 0"));
  }

  @ParameterizedTest
  @MethodSource("provideInvalidTokenExpiration")
  @DisplayName("유효하지 않은 Duration 값은 IllegalStateException을 발생시킨다")
  void fail_shouldThrowIllegalStateException_whenDurationIsInvalid(
      Duration accessTokenExpiration, Duration refreshTokenExpiration, String description) {
    // when & then
    assertThrows(
        IllegalStateException.class,
        () -> new JwtProperties(validKey, accessTokenExpiration, refreshTokenExpiration));
  }

  @Test
  @DisplayName("비밀키가 null이면 IllegalStateException이 발생한다")
  void fail_shouldThrowIllegalStateException_whenSecretKeyIsNull() {
    // when & then
    assertThrows(
        IllegalStateException.class,
        () -> new JwtProperties(null, accessTokenExpiration, refreshTokenExpiration));
  }

  @Test
  @DisplayName("비밀키가 32바이트 미만이면 IllegalStateException이 발생한다")
  void fail_shouldThrowIllegalStateException_whenSecretKeyUnder32Bytes() {
    // given
    String shortKey = "1234";

    // when & then
    assertThrows(
        IllegalStateException.class,
        () -> new JwtProperties(shortKey, accessTokenExpiration, refreshTokenExpiration));
  }

  @Test
  @DisplayName("비밀키가 32바이트 이상이면 객체가 정상생성 된다")
  void success_shouldCreateJwtProperties_whenSecretKeyGreaterThanOrEqualTo32Bytes() {
    // when & then
    assertDoesNotThrow(
        () -> new JwtProperties(validKey, accessTokenExpiration, refreshTokenExpiration));
  }
}
