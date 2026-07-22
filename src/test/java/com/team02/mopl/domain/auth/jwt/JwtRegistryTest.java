package com.team02.mopl.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.jwt.JwtRegistry.AuthCheckResult;
import com.team02.mopl.domain.auth.jwt.JwtRegistry.RotationResult;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.global.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtRegistryTest {

  @Mock private JwtUtils jwtUtils;
  @Mock private JwtProperties properties;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private ValueOperations<String, String> valueOperations;
  @InjectMocks private JwtRegistry jwtRegistry;

  private String accessToken;
  private String refreshToken;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(jwtRegistry, "refreshPrefix", "jwt:refresh:");
    ReflectionTestUtils.setField(jwtRegistry, "maxAccountCount", 1L);

    accessToken = "accessToken";
    refreshToken = "refreshToken";
  }

  @Nested
  class RegisterToken {

    @Test
    @DisplayName("네트워크 오류가 생기면 예외를 던진다")
    void fail_shouldNotRemoveToken_whenCurrentCountIsNull() {
      // given
      UUID userId = UUID.randomUUID();
      given(properties.refreshTokenExpiration()).willReturn(Duration.ZERO);
      given(jwtUtils.getRemainingTimeToExpiration(anyString())).willReturn(Duration.ZERO);
      given(
              redisTemplate.execute(
                  any(), anyList(), any(), any(), any(), any(), any(), any(), any()))
          .willThrow(RedisConnectionFailureException.class);

      // when & then
      assertThrows(
          BusinessException.class,
          () -> jwtRegistry.registerToken(userId, refreshToken, accessToken));
    }

    @Test
    @DisplayName("userId, refreshToken, accessToken을 가지고 redis에 저장한다")
    void success_shouldSaveRefreshAndAccessToken_whenUserIdAndRefreshTokenAndAccessTokenHas() {
      // given
      UUID userId = UUID.randomUUID();
      given(properties.refreshTokenExpiration()).willReturn(Duration.ZERO);
      given(jwtUtils.getRemainingTimeToExpiration(anyString())).willReturn(Duration.ZERO);
      given(
              redisTemplate.execute(
                  any(), anyList(), any(), any(), any(), any(), any(), any(), any()))
          .willReturn("OK");

      // when
      assertDoesNotThrow(() -> jwtRegistry.registerToken(userId, refreshToken, accessToken));
      then(redisTemplate)
          .should(times(1))
          .execute(any(), anyList(), any(), any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  class DeleteRefreshToken {

    @Test
    @DisplayName("refresh토큰이 잘못된 토큰이어도 삭제요청을 진행한다")
    void success_shouldAttemptToRemove_whenRefreshIsInvalid() {
      // given
      String refreshToken = "invalid token";
      given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

      // when
      jwtRegistry.deleteRefreshToken(UUID.randomUUID(), refreshToken);

      // then
      then(zSetOperations).should(times(1)).remove(anyString(), eq(refreshToken));
    }
  }

  @Nested
  class RegisterBlacklist {

    private String accessTokenId;
    private Duration remaining;

    @BeforeEach
    void SetUp() {
      accessTokenId = "token id";
      remaining = Duration.ofMinutes(5);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -2})
    @DisplayName("액세스 토큰 시간이 음수이거나 0이면 블랙리스트 등록을 스킵한다")
    void success_shouldSkipBlacklist_whenTokenRemainingTimeIsNegativeOrZero(long minutes) {
      // given
      remaining = Duration.ofMinutes(minutes);

      // when
      jwtRegistry.registerBlacklist(accessTokenId, remaining);

      // then
      then(valueOperations).should(never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("액세스 토큰 시간이 양수면 블랙리스트 등록을 진행한다")
    void success_shouldAddToBlacklist_whenTokenRemainingTimeIsPositive() {
      // given
      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      // when
      jwtRegistry.registerBlacklist(accessTokenId, remaining);

      // then
      then(valueOperations).should(times(1)).set(anyString(), eq("logout"), eq(remaining));
    }
  }

  @Nested
  class RotateRefreshToken {

    private UUID userId;
    private String refreshToken;
    private String newRefreshToken;
    private String newAccessToken;

    @BeforeEach
    void SetUp() {
      userId = UUID.randomUUID();
      refreshToken = "refresh-token";
      newRefreshToken = "new-refresh-token";
      newAccessToken = "new-access-token";
    }

    @Test
    @DisplayName("네트워크 오류가 생기면 예외를 던진다")
    void fail_shouldNotRemoveToken_whenCurrentCountIsNull() {
      // given
      given(properties.refreshTokenExpiration()).willReturn(Duration.ZERO);
      given(jwtUtils.getRemainingTimeToExpiration(anyString()))
          .willReturn(Duration.ofMinutes(1).negated());
      given(
              redisTemplate.execute(
                  any(), anyList(), any(), any(), any(), any(), any(), any(), any()))
          .willThrow(RedisConnectionFailureException.class);

      // when & then
      assertThrows(
          BusinessException.class,
          () ->
              jwtRegistry.rotateRefreshToken(
                  userId, refreshToken, newRefreshToken, newAccessToken));
    }

    @Test
    @DisplayName("스크립트 실행이 오류가 난다면 Invalid를 반환한다")
    void fail_shouldReturnInvalidResult_whenScriptErrors() {
      // given
      given(properties.refreshTokenExpiration()).willReturn(Duration.ZERO);
      given(jwtUtils.getRemainingTimeToExpiration(anyString())).willReturn(Duration.ZERO);
      given(
              redisTemplate.execute(
                  any(), anyList(), any(), any(), any(), any(), any(), any(), any()))
          .willReturn(null);

      // when
      RotationResult result =
          jwtRegistry.rotateRefreshToken(userId, refreshToken, newRefreshToken, newAccessToken);

      // then
      assertThat(result).isEqualTo(RotationResult.INVALID);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1, 2})
    @DisplayName("redis의 결과값에 따라 그에 맞는 RotationResult 타입을 반환한다")
    void success_shouldReturnCorrespondingRotationResult_whenRedisReturnsResultCode(
        long redisResult) {
      // given
      given(properties.refreshTokenExpiration()).willReturn(Duration.ZERO);
      given(jwtUtils.getRemainingTimeToExpiration(anyString())).willReturn(Duration.ZERO);
      given(
              redisTemplate.execute(
                  any(), anyList(), any(), any(), any(), any(), any(), any(), any()))
          .willReturn(redisResult);
      RotationResult expected =
          switch ((int) redisResult) {
            case 0 -> RotationResult.OK;
            case 1 -> RotationResult.INVALID;
            default -> RotationResult.COMPROMISED;
          };

      // when
      RotationResult result =
          jwtRegistry.rotateRefreshToken(userId, refreshToken, newRefreshToken, newAccessToken);

      // then
      assertThat(result).isEqualTo(expected);
    }
  }

  @Nested
  class DeleteAllRefreshToken {
    @Test
    @DisplayName("리프레시 토큰을 전체 삭제한다")
    void success_shouldDeleteAllRefreshToken_whenUserIdIsProvided() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      jwtRegistry.deleteAllRefreshToken(userId);

      // then
      then(redisTemplate).should(times(1)).delete(anyString());
    }
  }

  @Nested
  class LockUser {
    @Test
    @DisplayName("유저ID가 주어지면 리프레시 토큰을 전체 삭제하고 유저 잠금키를 추가한다")
    void success_shouldDeleteAllRefreshTokenAndLockKeyUserId_whenUserIdIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(properties.accessTokenExpiration()).willReturn(mock(Duration.class));

      // when
      jwtRegistry.lockUser(userId);

      // then
      then(redisTemplate).should(times(1)).delete(anyString());
      then(valueOperations).should(times(1)).set(anyString(), anyString(), any(Duration.class));
    }
  }

  @Nested
  class UnlockUser {
    @Test
    @DisplayName("유저ID가 주어지면 유저 잠금키를 제거한다")
    void success_shouldDeleteUserLockKey_whenUserIdIsProvided() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      jwtRegistry.unlockUser(userId);

      // then
      then(redisTemplate).should(times(1)).delete(anyString());
    }
  }

  @Nested
  class CheckAuthStatus {

    private UUID userId;
    private String accessTokenId;
    private String accessToken;

    @BeforeEach
    void setUp() {
      userId = UUID.randomUUID();
      accessTokenId = UUID.randomUUID().toString();
      accessToken = "access-token";
    }

    @Test
    @DisplayName("액세스토큰ID, 유저ID와 액세스토큰이 주어지면 블랙리스트, 유저잠금상태와 액세스토큰 활성화 상태를 반환한다")
    void success_shouldReturnAuthCheckResult_whenAccessTokenIdAndUserIdAreProvided() {
      // given
      String accessTokenId = UUID.randomUUID().toString();
      UUID userId = UUID.randomUUID();

      List<Long> executeResult = List.of(0L, 0L, 1L);
      given(redisTemplate.execute(any(), anyList(), eq(accessToken))).willReturn(executeResult);

      // when
      AuthCheckResult result = jwtRegistry.checkAuthStatus(accessTokenId, userId, accessToken);

      // then
      assertThat(result.isBlacklisted()).isFalse();
      assertThat(result.isUserLocked()).isFalse();
      assertThat(result.isAccessTokenActive()).isTrue();
    }

    @Test
    @DisplayName("스크립트 실행이 오류가 난다면 Invalid를 반환한다")
    void fail_shouldReturnInvalidResult_whenScriptErrors() {
      // given
      given(redisTemplate.execute(any(), anyList(), any())).willReturn(null);

      // when & then
      assertThrows(
          BusinessException.class,
          () -> jwtRegistry.checkAuthStatus(accessTokenId, userId, accessToken));
    }

    @Test
    @DisplayName("redis의 문제가 생기면 예외를 던진다")
    void fail_shouldThrowException_whenRedisIsDownDuringCheckAuthStatus() {
      // given
      willThrow(RedisConnectionFailureException.class)
          .given(redisTemplate)
          .execute(any(), anyList(), any());

      // when & then
      assertThrows(
          InternalAuthenticationServiceException.class,
          () -> jwtRegistry.checkAuthStatus(accessTokenId, userId, accessToken));
    }
  }

  @Nested
  class RegisterTempPassword {
    @Test
    @DisplayName("임시패스워드를 추가하는데 성공하면 true를 반환한다")
    void success_shouldReturnTrue_whenRegistrationSucceeds() {
      // given
      UUID userId = UUID.randomUUID();
      String tempPassword = "tmpPassword";
      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      // when
      boolean actual =
          jwtRegistry.registerTempPassword(userId, tempPassword, Duration.ofMinutes(3));

      // then
      assertThat(actual).isTrue();
    }

    @Test
    @DisplayName("레디스에 문제가 생기면 false를 반환한다")
    void fail_shouldReturnFail_whenRedisThrowsException() {
      // given
      UUID userId = UUID.randomUUID();
      String tempPassword = "tmpPassword";
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      willThrow(RedisConnectionFailureException.class)
          .given(valueOperations)
          .set(anyString(), eq(tempPassword), any(Duration.class));

      // when & then
      boolean actual =
          assertDoesNotThrow(
              () -> jwtRegistry.registerTempPassword(userId, tempPassword, Duration.ofMinutes(3)));
      assertThat(actual).isFalse();
    }
  }

  @Nested
  class DeleteTempPassword {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("임시패스워드를 제거하는데 성공하면 true를 반환한다")
    void success_shouldReturnTrue_whenDeletionResultIsProvided(boolean isDeleted) {
      // given
      given(redisTemplate.delete(anyString())).willReturn(isDeleted);

      // when
      boolean actual = jwtRegistry.deleteTempPassword(UUID.randomUUID());

      // then
      assertThat(actual).isTrue();
    }

    @Test
    @DisplayName("레디스에 문제가 생기면 false를 반환한다")
    void fail_shouldReturnFail_whenRedisThrowsException() {
      // given
      willThrow(RedisConnectionFailureException.class).given(redisTemplate).delete(anyString());

      // when & then
      boolean actual = assertDoesNotThrow(() -> jwtRegistry.deleteTempPassword(UUID.randomUUID()));
      assertThat(actual).isFalse();
    }
  }

  @Nested
  class GetTempPassword {
    private static Stream<String> provideRedisValues() {
      return Stream.of("temporary_password_123", null);
    }

    @ParameterizedTest
    @MethodSource("provideRedisValues")
    @DisplayName("유저ID로 키를 조회해 임시 패스워드값을 반환한다")
    void success_shouldReturnTempPasswordOrNull_whenRedisLookupSucceeds(String expectedValue) {
      // given
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.get(anyString())).willReturn(expectedValue);

      // when
      String actual = jwtRegistry.getTempPassword(UUID.randomUUID());

      // then
      assertThat(actual).isEqualTo(expectedValue);
    }

    @Test
    @DisplayName("레디스에 문제가 생기면 false를 반환한다")
    void fail_shouldReturnFail_whenRedisThrowsException() {
      // given
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      willThrow(RedisConnectionFailureException.class).given(valueOperations).get(anyString());

      // when & then
      String actual = assertDoesNotThrow(() -> jwtRegistry.getTempPassword(UUID.randomUUID()));
      assertThat(actual).isNull();
    }
  }

  @Nested
  class VerifyAndUserTempPassword {

    @Test
    @DisplayName("임시 비밀번호가 일치하면 Redis에서 삭제하고 true를 반환한다")
    void success_shouldDeleteTempPasswordAndReturnTrue_whenPasswordMatches() {
      // given
      given(redisTemplate.execute(any(), any(), any())).willReturn(1L);

      // when
      boolean result = jwtRegistry.verifyAndUseTempPassword(UUID.randomUUID(), "inputPassword");

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("임시 비밀번호가 일치하지 않으면 false를 반환한다")
    void success_shouldReturnFalse_whenPasswordDoesNotMatch() {
      // given
      given(redisTemplate.execute(any(), any(), any())).willReturn(0L);

      // when
      boolean result = jwtRegistry.verifyAndUseTempPassword(UUID.randomUUID(), "inputPassword");

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Redis 장애로 null을 반환하면 예외를 터트리지 않고 false를 반환한다")
    void success_ShouldReturnFalse_whenRedisIsDown() {
      // given
      willThrow(RedisConnectionFailureException.class)
          .given(redisTemplate)
          .execute(any(), any(), any());

      // when
      boolean result = jwtRegistry.verifyAndUseTempPassword(UUID.randomUUID(), "inputPassword");

      // then
      assertThat(result).isFalse();
    }
  }
}
