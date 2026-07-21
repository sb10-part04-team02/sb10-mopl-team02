package com.team02.mopl.domain.content.ingestion.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.content.ingestion.batch.IngestionMode;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class IngestionRunLockTest {

  private static final Duration TTL = Duration.ofMinutes(30);

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private IngestionRunLock runLock;

  @Test
  @DisplayName("락이 비어 있으면 획득에 성공하고 해제용 토큰을 반환한다")
  void tryAcquire_whenLockFree_returnsToken() {
    // given
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(eq("ingestion:collect:lock"), anyString(), eq(TTL)))
        .willReturn(true);

    // when
    String token = runLock.tryAcquire(TTL);

    // then
    assertThat(token).isNotNull();
  }

  @Test
  @DisplayName("이미 잠겨 있으면 null을 반환한다")
  void tryAcquire_whenAlreadyLocked_returnsNull() {
    // given
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.setIfAbsent(eq("ingestion:collect:lock"), anyString(), eq(TTL)))
        .willReturn(false);

    // when
    String token = runLock.tryAcquire(TTL);

    // then
    assertThat(token).isNull();
  }

  @Test
  @DisplayName("해제 시 락 키와 토큰으로 compare-and-delete 스크립트를 실행한다")
  void release_executesScriptWithKeyAndToken() {
    // when
    runLock.release("token");

    // then
    then(redisTemplate)
        .should()
        .execute(any(RedisScript.class), eq(List.of("ingestion:collect:lock")), eq("token"));
  }

  @Test
  @DisplayName("모드 오버로드는 모드별 접미 키로 획득한다 (DAILY/HOURLY가 서로 다른 키)")
  void tryAcquire_withMode_usesModeSpecificKey() {
    // given
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(
            valueOperations.setIfAbsent(
                eq("ingestion:collect:lock:" + IngestionMode.HOURLY.name()), anyString(), eq(TTL)))
        .willReturn(true);

    // when
    String token = runLock.tryAcquire(IngestionMode.HOURLY, TTL);

    // then
    assertThat(token).isNotNull();
  }

  @Test
  @DisplayName("모드 오버로드 해제는 모드별 접미 키로 스크립트를 실행한다")
  void release_withMode_usesModeSpecificKey() {
    // when
    runLock.release(IngestionMode.DAILY, "token");

    // then
    then(redisTemplate)
        .should()
        .execute(
            any(RedisScript.class),
            eq(List.of("ingestion:collect:lock:" + IngestionMode.DAILY.name())),
            eq("token"));
  }
}
