package com.team02.mopl.domain.content.ingestion.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// 수집 중복 실행 방지용 Redis try-lock. 다중 인스턴스 환경에서 한 인스턴스만 수집을 실행하도록 보장
// - TTL로 자동 만료되어 프로세스 크래시 시에도 락이 영구히 남지 않음
// - 해제는 토큰 비교 후 삭제(Lua)로, TTL 만료 후 다른 인스턴스가 잡은 락을 오삭제하지 않음
@Component
@RequiredArgsConstructor
public class IngestionRunLock {

  private static final String LOCK_KEY = "ingestion:collect:lock";
  private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(
          "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
          Long.class);

  private final StringRedisTemplate redisTemplate;

  // 획득 성공 시 해제용 토큰을, 이미 잠겨 있으면 null을 반환한다
  public String tryAcquire(Duration ttl) {
    String token = UUID.randomUUID().toString();
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, ttl);
    return Boolean.TRUE.equals(acquired) ? token : null;
  }

  public void release(String token) {
    redisTemplate.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), token);
  }
}
