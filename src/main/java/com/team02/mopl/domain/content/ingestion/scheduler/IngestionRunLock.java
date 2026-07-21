package com.team02.mopl.domain.content.ingestion.scheduler;

import com.team02.mopl.domain.content.ingestion.batch.IngestionMode;
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
// - 기본 키는 모든 수집 경로가 공유한다. 모드별로 독립 실행돼야 하는 경우(DAILY/HOURLY 스케줄이 같은 시각에
//   겹치는 스케줄러)는 모드 오버로드로 별도 키를 써서 서로를 막지 않게 한다.
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

  // 기본(공유) 키로 획득. 획득 성공 시 해제용 토큰을, 이미 잠겨 있으면 null을 반환
  public String tryAcquire(Duration ttl) {
    return tryAcquire(LOCK_KEY, ttl);
  }

  // 모드별 독립 키로 획득. DAILY/HOURLY가 같은 시각에 겹쳐도 서로의 실행을 막지 않는다
  public String tryAcquire(IngestionMode mode, Duration ttl) {
    return tryAcquire(keyFor(mode), ttl);
  }

  // 기본(공유) 키 해제
  public void release(String token) {
    release(LOCK_KEY, token);
  }

  // 모드별 독립 키 해제
  public void release(IngestionMode mode, String token) {
    release(keyFor(mode), token);
  }

  private String tryAcquire(String lockKey, Duration ttl) {
    String token = UUID.randomUUID().toString();
    // setIfAbsent = Redis의 SET key value NX PX ttl 명령
    Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, token, ttl);
    return Boolean.TRUE.equals(acquired) ? token : null;
  }

  private void release(String lockKey, String token) {
    redisTemplate.execute(RELEASE_SCRIPT, List.of(lockKey), token);
  }

  private static String keyFor(IngestionMode mode) {
    return LOCK_KEY + ":" + mode.name();
  }
}
