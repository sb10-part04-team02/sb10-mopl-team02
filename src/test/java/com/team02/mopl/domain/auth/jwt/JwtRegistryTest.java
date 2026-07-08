package com.team02.mopl.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
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

  @Mock private JwtProperties properties;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @Mock private ValueOperations<String, String> valueOperations;
  @InjectMocks private JwtRegistry jwtRegistry;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(jwtRegistry, "refreshPrefix", "jwt:refresh:");
    ReflectionTestUtils.setField(jwtRegistry, "maxAccountCount", 1L);
  }

  @Nested
  class RegisterRefreshToken {
    @Test
    @DisplayName("네트워크 오류로 토큰 개수가 조회되지 않으면 삭제를 건너뛴다")
    void success_shouldNotRemoveToken_whenCurrentCountIsNull() {
      // given
      UUID userId = UUID.randomUUID();
      String key = "jwt:refresh:" + userId;
      String refreshToken = "refreshToken";
      Duration expiration = Duration.ofMinutes(10);
      given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

      given(properties.refreshTokenExpiration()).willReturn(expiration);
      given(zSetOperations.size(anyString())).willReturn(null);

      // when
      jwtRegistry.registerRefreshToken(userId, refreshToken);

      // then
      then(zSetOperations).should(never()).removeRange(eq(key), anyLong(), anyLong());
    }

    @Test
    @DisplayName("현재개수가 maxAccountCount보다 많으면 maxAccountCount 될때까지 삭제한다")
    void success_shouldRemoveOldestToken_whenExceedMaxAccountCount() {
      // given
      UUID userId = UUID.randomUUID();
      String key = "jwt:refresh:" + userId;
      String refreshToken = "refreshToken";
      Duration expiration = Duration.ofMinutes(10);
      given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

      given(properties.refreshTokenExpiration()).willReturn(expiration);
      given(zSetOperations.size(anyString())).willReturn(2L);

      // when
      jwtRegistry.registerRefreshToken(userId, refreshToken);

      // then
      then(zSetOperations).should(times(1)).removeRangeByScore(eq(key), eq(0.0), anyDouble());
      then(zSetOperations).should(times(1)).add(eq(key), eq(refreshToken), anyDouble());
      then(zSetOperations).should(times(1)).removeRange(eq(key), anyLong(), anyLong());
      then(redisTemplate).should(times(1)).expire(eq(key), eq(expiration));
    }

    @Test
    @DisplayName("userId와 refreshToken을 가지고 redis에 저장한다")
    void success_shouldSaveRefreshToken_whenUserIdAndRefreshTokenHas() {
      // given
      UUID userId = UUID.randomUUID();
      String key = "jwt:refresh:" + userId;
      String refreshToken = "refreshToken";
      Duration expiration = Duration.ofMinutes(10);
      given(redisTemplate.opsForZSet()).willReturn(zSetOperations);

      given(properties.refreshTokenExpiration()).willReturn(expiration);

      // when
      jwtRegistry.registerRefreshToken(userId, refreshToken);

      // then
      then(zSetOperations).should(times(1)).removeRangeByScore(eq(key), eq(0.0), anyDouble());
      then(zSetOperations).should(times(1)).add(eq(key), eq(refreshToken), anyDouble());
      then(zSetOperations).should(times(1)).size(eq(key));
      then(zSetOperations).should(never()).removeRange(eq(key), anyLong(), anyLong());
      then(redisTemplate).should(times(1)).expire(eq(key), eq(expiration));
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

    @BeforeEach
    void SetUp() {
      userId = UUID.randomUUID();
      refreshToken = "refresh token";
      newRefreshToken = "new refresh token";
      given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
    }

    @Test
    @DisplayName("refresh토큰이 없다면 키를 전체삭제하고 COMPROMISED 결과값을 반환한다")
    void fail_shouldDeleteAllAndReturnCompromisedResult_whenRefreshTokenDoesNotExist() {
      // given
      given(zSetOperations.score(anyString(), anyString())).willReturn(null);

      // when
      RotationResult actual = jwtRegistry.rotateRefreshToken(userId, refreshToken, newRefreshToken);

      // then
      then(redisTemplate).should(times(1)).delete(anyString());
      assertThat(actual).isEqualTo(RotationResult.COMPROMISED);
    }

    @Test
    @DisplayName("refresh토큰이 있다면 rotate과정을 진행한다")
    void success_shouldProcessRotate_whenRefreshTokenExists() {
      // given
      given(zSetOperations.score(anyString(), eq(refreshToken))).willReturn(3.0);
      given(properties.refreshTokenExpiration()).willReturn(Duration.ofMinutes(10));

      // when
      RotationResult actual = jwtRegistry.rotateRefreshToken(userId, refreshToken, newRefreshToken);

      // then
      then(zSetOperations).should(times(1)).remove(anyString(), eq(refreshToken));
      then(zSetOperations).should(times(1)).add(anyString(), eq(newRefreshToken), anyDouble());
      then(redisTemplate).should(times(1)).expire(anyString(), any(Duration.class));
      assertThat(actual).isEqualTo(RotationResult.OK);
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
    @Test
    @DisplayName("액세스토큰ID와 유저ID가 주어지면 블랙리스트와 유저잠금상태를 반환한다")
    void success_shouldReturnAuthCheckResult_whenAccessTokenIdAndUserIdAreProvided() {
      // given
      String accessTokenId = UUID.randomUUID().toString();
      UUID userId = UUID.randomUUID();

      given(redisTemplate.hasKey(anyString())).willReturn(true);

      // when
      AuthCheckResult result = jwtRegistry.checkAuthStatus(accessTokenId, userId);

      // then
      assertThat(result.isBlacklisted()).isTrue();
      assertThat(result.isUserLocked()).isTrue();
    }

    @Test
    @DisplayName("redis의 문제가 생기면 예외를 던진다")
    void fail_shouldThrowException_whenRedisIsDownDuringCheckAuthStatus() {
      // given
      String accessTokenId = UUID.randomUUID().toString();
      UUID userId = UUID.randomUUID();

      willThrow(RedisConnectionFailureException.class).given(redisTemplate).hasKey(anyString());

      // when & then
      assertThrows(
          InternalAuthenticationServiceException.class,
          () -> jwtRegistry.checkAuthStatus(accessTokenId, userId));
    }
  }
}
