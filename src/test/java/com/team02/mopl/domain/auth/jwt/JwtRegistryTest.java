package com.team02.mopl.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
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

  @Nested
  class DeleteRefreshToken {

    private UUID userId;
    private String accessTokenId;
    private Duration remaining;
    private String refreshToken;

    @BeforeEach
    void SetUp() {
      userId = UUID.randomUUID();
      accessTokenId = "token id";
      remaining = Duration.ofMinutes(5);
      refreshToken = "refresh token";

      given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
    }

    @Test
    @DisplayName("refresh토큰이 잘못된 토큰이어도 삭제요청을 진행한다")
    void success_shouldAttemptToRemove_whenRefreshIsInvalid() {
      // given
      refreshToken = "invalid token";
      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      // when
      jwtRegistry.deleteRefreshToken(userId, accessTokenId, remaining, refreshToken);

      // then
      then(zSetOperations).should(times(1)).remove(anyString(), eq(refreshToken));
      then(valueOperations).should(times(1)).set(anyString(), eq("logout"), eq(remaining));
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -2})
    @DisplayName("액세스 토큰 시간이 음수이거나 0이면 블랙리스트 등록을 스킵한다")
    void success_shouldSkipBlacklist_whenTokenRemainingTimeIsNegativeOrZero(long minutes) {
      // given
      remaining = Duration.ofMinutes(minutes);

      // when
      jwtRegistry.deleteRefreshToken(userId, accessTokenId, remaining, refreshToken);

      // then
      then(zSetOperations).should(times(1)).remove(anyString(), eq(refreshToken));
      then(valueOperations).should(never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("액세스 토큰 시간이 양수면 블랙리스트 등록을 진행한다")
    void success_shouldAddToBlacklist_whenTokenRemainingTimeIsPositive() {
      // given
      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      // when
      jwtRegistry.deleteRefreshToken(userId, accessTokenId, remaining, refreshToken);

      // then
      then(valueOperations).should(times(1)).set(anyString(), eq("logout"), eq(remaining));
    }
  }

  @Nested
  class IsBlacklisted {
    @Test
    @DisplayName("network오류로 null이 들어온다면 false를 반환한다")
    void fail_shouldReturnFalse_whenRedisReturnsNullDueToNetworkError() {
      // given
      given(redisTemplate.hasKey(any())).willReturn(null);

      // when
      boolean actual = jwtRegistry.isBlacklisted(UUID.randomUUID().toString());

      // then
      assertThat(actual).isFalse();
    }

    @Test
    @DisplayName("blackList에 등록되어 있지 않으면 false를 반환한다")
    void success_shouldReturnFalse_whenTokenIsNotBlacklisted() {
      // given
      given(redisTemplate.hasKey(any())).willReturn(false);

      // when
      boolean actual = jwtRegistry.isBlacklisted(UUID.randomUUID().toString());

      // then
      assertThat(actual).isFalse();
    }

    @Test
    @DisplayName("blackList에 등록되어 있으면 return 반환한다")
    void success_shouldReturnTrue_whenTokenIsBlacklisted() {
      // given
      given(redisTemplate.hasKey(any())).willReturn(true);

      // when
      boolean actual = jwtRegistry.isBlacklisted(UUID.randomUUID().toString());

      // then
      assertThat(actual).isTrue();
    }
  }
}
