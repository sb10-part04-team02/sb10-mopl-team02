package com.team02.mopl.global.redis;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockManager {

  private static final String LOCK_KEY_PREFIX = "lock:";
  private final StringRedisTemplate redisTemplate;
  private static final RedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
          Long.class);

  public String acquireLock(String key, Duration duration) {
    String lockKey = LOCK_KEY_PREFIX + key;
    String lockValue = UUID.randomUUID().toString();

    try {
      Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, duration);
      if (Boolean.TRUE.equals(acquired)) {
        log.debug("분산 락 획득 성공: key={}, value={}", lockKey, lockValue);
        return lockValue;
      }
      log.debug("분산 락 획득 실패: key={}", lockKey);
    } catch (Exception e) {
      log.error("[Redis] 락 설정 중 장애 발생: key={}, reason={}", lockKey, e.getMessage(), e);
    }

    return null;
  }

  public void releaseLock(String key, String lockValue) {
    String lockKey = LOCK_KEY_PREFIX + key;

    try {
      Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey), lockValue);

      if (Long.valueOf(1).equals(result)) {
        log.debug("분산 락 해제 성공: key={}", lockKey);
      } else {
        // 만료되서 없거나 다른 스레드가 진행중인 경우
        log.debug("분산 락 해제 실패: key={}", lockKey);
      }
    } catch (Exception e) {
      log.error("[Redis] 락 해제 중 장애 발생: key={}, reason={}", lockKey, e.getMessage(), e);
    }
  }
}
