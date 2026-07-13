package com.team02.mopl.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisLockManagerTest {

  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private StringRedisTemplate redisTemplate;
  @InjectMocks private RedisLockManager lockManager;

  private final String key = "key";
  private final Duration duration = Duration.ofSeconds(5);

  @Nested
  class AcquireLock {
    @Test
    @DisplayName("락이 비어있을 때 value를 반환한다")
    void success_shouldReturnValue_whenLockIsAvailable() {
      // given
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
          .willReturn(true);

      // when & then
      String result = lockManager.acquireLock(key, duration);
      assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("redis를 사용할 수 없을때 예외를 핸들링하고 null을 반환한다")
    void fail_shouldHandleErrorAndReturnNull_whenRedisIsUnavailable() {
      // given
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
          .willThrow(RedisConnectionFailureException.class);

      // when & then
      String result = assertDoesNotThrow(() -> lockManager.acquireLock(key, duration));
      assertThat(result).isNull();
    }

    @Test
    @DisplayName("이미 락이 차있을때 null을 반환한다")
    void fail_shouldReturnNull_whenLockIsAlreadyTaken() {
      // given
      given(redisTemplate.opsForValue()).willReturn(valueOperations);
      given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
          .willReturn(false);

      // when & then
      String result = lockManager.acquireLock(key, duration);
      assertThat(result).isNull();
    }
  }

  @Nested
  class ReleaseLock {
    @Test
    @DisplayName("키를 획득하면 락을 해제한다")
    void success_shouldReleaseKey_whenLockOwnerMatches() {
      // given
      given(redisTemplate.execute(any(), anyList(), any())).willReturn(1L);

      // when
      lockManager.releaseLock(key, "lockValue");

      // then
      then(redisTemplate).should(times(1)).execute(any(), anyList(), any());
    }

    @Test
    @DisplayName("redis를 사용할 수 없을때에도 예외를 던지지 않고 종료된다")
    void fail_shouldHandleSafely_whenRedisIsUnavailable() {
      // given
      given(redisTemplate.execute(any(), anyList(), any()))
          .willThrow(RedisConnectionFailureException.class);

      // when & then
      assertDoesNotThrow(() -> lockManager.releaseLock(key, "lockValue"));
    }

    @Test
    @DisplayName("키가 만료됐거나 다른 스레드가 선점중일 경우 바로 종료된다")
    void fail_shouldHandleSafely_whenLockNotExistsOrNotOwned() {
      // given
      given(redisTemplate.execute(any(), anyList(), any())).willReturn(0L);

      // when
      lockManager.releaseLock(key, "lockValue");

      // then
      then(redisTemplate).should(times(1)).execute(any(), anyList(), any());
    }
  }
}
