package com.team02.mopl.global.outbox.redis.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.repository.RedisCommandOutboxRepository;
import com.team02.mopl.global.outbox.redis.service.RedisOutboxService;
import com.team02.mopl.global.redis.RedisLockManager;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RedisOutboxSchedulerTest {

  @Mock private RedisCommandOutboxRepository outboxRepository;
  @Mock private RedisLockManager lockManager;
  @Mock private RedisOutboxService outboxService;
  @InjectMocks private RedisOutboxScheduler outboxScheduler;

  @Nested
  class RetryFailRedisCommands {

    @Test
    @DisplayName("락을 획득하면 outbox처리후 락을 반환해야 한다")
    void success_shouldProcessOutboxAndReleaseLock_whenLockAcquiredSuccessfully() {
      // given
      String lockValue = "lockValue";
      given(lockManager.acquireLock(anyString(), any(Duration.class))).willReturn(lockValue);

      int retryCountThreshold = 5;
      given(outboxService.getRetryCountThreshold()).willReturn(retryCountThreshold);
      RedisCommandOutbox mockOutbox = mock(RedisCommandOutbox.class);
      given(
              outboxRepository
                  .findTop1000ByProcessedFalseAndDeletedAtIsNullAndRetryCountLessThanEqualOrderByCreatedAtAsc(
                      retryCountThreshold))
          .willReturn(List.of(mockOutbox));

      // when
      outboxScheduler.retryFailRedisCommands();

      // then
      then(outboxService).should(times(1)).processOutbox(mockOutbox);
      then(lockManager).should(times(1)).releaseLock(anyString(), eq(lockValue));
    }

    @Test
    @DisplayName("락획득에 실패하면 처리없이 조기 반환한다")
    void fail_shouldReturnEarlyWithoutProcessing_whenLockAcquisitionFails() {
      // given
      given(lockManager.acquireLock(anyString(), any(Duration.class))).willReturn(null);

      // when
      outboxScheduler.retryFailRedisCommands();

      // then
      then(outboxRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재시도 카운트 증가 도중 예외가 발생해도 스케줄러를 차단하지 않고 다음 outbox를 처리한다")
    void success_shouldContinueProcessingNextOutbox_whenRetryCountIncrementFails() {
      // given
      String lockValue = "lockValue";
      given(lockManager.acquireLock(anyString(), any(Duration.class))).willReturn(lockValue);

      int retryCountThreshold = 5;
      given(outboxService.getRetryCountThreshold()).willReturn(retryCountThreshold);
      RedisCommandOutbox mockOutbox = mock(RedisCommandOutbox.class);
      given(
              outboxRepository
                  .findTop1000ByProcessedFalseAndDeletedAtIsNullAndRetryCountLessThanEqualOrderByCreatedAtAsc(
                      retryCountThreshold))
          .willReturn(List.of(mockOutbox));

      willThrow(IllegalArgumentException.class).given(outboxService).processOutbox(mockOutbox);
      willThrow(RuntimeException.class).given(outboxService).increaseRetryCount(mockOutbox);

      // when
      outboxScheduler.retryFailRedisCommands();

      // then
      then(outboxService).should().processOutbox(mockOutbox);
      then(outboxService).should().increaseRetryCount(mockOutbox);
      then(lockManager).should(times(1)).releaseLock(anyString(), eq(lockValue));
    }

    @Test
    @DisplayName("outbox처리중 예외가 발생해도 락을 반환해야 한다")
    void success_shouldReleaseLock_whenExceptionOccurs() {
      // given
      String lockValue = "lockValue";
      given(lockManager.acquireLock(anyString(), any(Duration.class))).willReturn(lockValue);

      int retryCountThreshold = 5;
      given(outboxService.getRetryCountThreshold()).willReturn(retryCountThreshold);
      RedisCommandOutbox mockOutbox = mock(RedisCommandOutbox.class);
      given(
              outboxRepository
                  .findTop1000ByProcessedFalseAndDeletedAtIsNullAndRetryCountLessThanEqualOrderByCreatedAtAsc(
                      retryCountThreshold))
          .willReturn(List.of(mockOutbox));

      willThrow(IllegalArgumentException.class).given(outboxService).processOutbox(mockOutbox);

      // when
      assertDoesNotThrow(() -> outboxScheduler.retryFailRedisCommands());

      // then
      then(outboxService).should(times(1)).increaseRetryCount(mockOutbox);
      then(lockManager).should(times(1)).releaseLock(anyString(), eq(lockValue));
    }
  }

  @Nested
  class CleanUp {

    @Test
    @DisplayName("락획득에 성공하면 소프트 삭제된 outbox를 삭제하고 락을 해제한다")
    void success_shouldDeleteSoftDeletedOutboxesAndReleaseLock_whenLockAcquiredSuccessfully() {
      // given
      String lockValue = "lockValue";
      given(lockManager.acquireLock(anyString(), any(Duration.class))).willReturn(lockValue);

      // when
      outboxScheduler.cleanUp();

      // then
      then(outboxService).should(times(1)).deleteAllOutboxProcessedIsTrue();
      then(lockManager).should(times(1)).releaseLock(anyString(), eq(lockValue));
    }

    @Test
    @DisplayName("락획득에 실패하면 처리없이 조기 반환한다")
    void fail_shouldReturnEarlyWithoutProcessing_whenLockAcquisitionFails() {
      // given
      given(lockManager.acquireLock(anyString(), any(Duration.class))).willReturn(null);

      // when
      outboxScheduler.cleanUp();

      // then
      then(outboxService).shouldHaveNoInteractions();
    }
  }
}
