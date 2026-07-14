package com.team02.mopl.global.outbox.redis.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;
import com.team02.mopl.global.outbox.redis.entity.enums.OutboxTarget;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class RedisCommandOutboxTest {

  @Test
  @DisplayName("markProcessed함수를 진행하면 processed 상태가 변한다")
  void success_shouldChangeProcessedToTrue_whenMarkProcessedInvoked() {
    // given
    RedisCommandOutbox outbox = createOutbox();

    // when
    outbox.markProcessed();

    // then
    assertThat(outbox.isProcessed()).isTrue();
  }

  @Test
  @DisplayName("incrementRetryCount함수를 진행하면 retryCount 값이 증가한다")
  void success_shouldIncreaseCountByOne_whenIncrementRetryCountInvoked() {
    // given
    RedisCommandOutbox outbox = createOutbox();
    int initRetryCount = outbox.getRetryCount();

    // when
    outbox.incrementRetryCount();

    // then
    assertThat(outbox.getRetryCount()).isEqualTo(initRetryCount + 1);
  }

  @ParameterizedTest
  @CsvSource({"3, true", "4, false"})
  @DisplayName("재시도 횟수가 임계값을 초과하는지 정확하게 판단한다")
  void success_shouldReturnCorrectBoolean_whenIsFailedPermanentlyInvoked(
      int threshold, boolean expected) {
    // given
    RedisCommandOutbox outbox = createOutbox();
    ReflectionTestUtils.setField(outbox, "retryCount", 4);

    // when
    boolean result = outbox.isFailedPermanently(threshold);

    // then
    assertThat(result).isEqualTo(expected);
  }

  private RedisCommandOutbox createOutbox() {
    return new RedisCommandOutbox(
        CommandType.DELETE_ALL_REFRESH_TOKEN, UUID.randomUUID(), OutboxTarget.USER);
  }
}
