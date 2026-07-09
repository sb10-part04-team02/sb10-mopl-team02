package com.team02.mopl.domain.content.ingestion.scheduler;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// 수집 중복 실행 방지용 Redis try-lock. 다중 인스턴스 환경에서 한 인스턴스만 수집을 실행하도록 보장
// - 1) 크래시로 인한 영구 락 -> TTL 자동 만료로 해결
// - 2) 남의 락 오삭제(TTL 만료 후 다른 인스턴스가 잡은 락을 실수로 지움) -> 토큰 검증 해제로 해결
@Component
@RequiredArgsConstructor
public class IngestionRunLock {

  private static final String LOCK_KEY = "ingestion:collect:lock"; // 모든 인스턴스가 공유하는 단일 락 키
  private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
      new DefaultRedisScript<>(
          // 현재 Redis에 저장된 값(락의 실제 주인 토큰)이 내 토큰과 같을 때만 삭제하고, 다르면 아무것도 하지 않고 0 반환
          "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
          Long.class);

  private final StringRedisTemplate redisTemplate;

  // 획득 성공 시 해제용 토큰을, 이미 잠겨 있으면 null을 반환
  public String tryAcquire(Duration ttl) {
    String token = UUID.randomUUID().toString();
    // setIfAbsent = Redis의 SET key value NX PX ttl 명령
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, ttl);
    return Boolean.TRUE.equals(acquired) ? token : null;
  }

  // 락 해제
  public void release(String token) {
    redisTemplate.execute(RELEASE_SCRIPT, List.of(LOCK_KEY), token);
  }
}
