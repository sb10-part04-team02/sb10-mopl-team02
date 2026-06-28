package com.team02.mopl.domain.auth.jwt;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JwtRegistryTest {

  @Mock private JwtProperties properties;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ZSetOperations<String, String> zSetOperations;
  @InjectMocks private JwtRegistry jwtRegistry;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(jwtRegistry, "refreshPrefix", "jwt:refresh:");
    ReflectionTestUtils.setField(jwtRegistry, "maxAccountCount", 1L);

    given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
  }

  @Test
  @DisplayName("네트워크 오류로 토큰 개수가 조회되지 않으면 삭제를 건너뛴다")
  void success_shouldNotRemoveToken_whenCurrentCountIsNull() {
    // given
    UUID userId = UUID.randomUUID();
    String key = "jwt:refresh:" + userId;
    String refreshToken = "refreshToken";
    Duration expiration = Duration.ofMinutes(10);

    given(properties.refreshTokenExpiration()).willReturn(expiration);
    given(zSetOperations.size(anyString())).willReturn(null);

    // when
    jwtRegistry.registerRefreshToken(userId, refreshToken);

    // then
    then(zSetOperations).should(never()).removeRange(eq(key), anyLong(), anyLong());
  }

  @ParameterizedTest
  @ValueSource(longs = {1L, 2L})
  @DisplayName("현재개수가 maxAccountCount보다 많으면 maxAccountCount 될때까지 삭제한다")
  void success_shouldRemoveOldestToken_whenExceedMaxAccountCount(Long currentCount) {
    // given
    UUID userId = UUID.randomUUID();
    String key = "jwt:refresh:" + userId;
    String refreshToken = "refreshToken";
    Duration expiration = Duration.ofMinutes(10);

    given(properties.refreshTokenExpiration()).willReturn(expiration);
    given(zSetOperations.size(anyString())).willReturn(3L);

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
