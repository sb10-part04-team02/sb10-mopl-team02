package com.team02.mopl.global.outbox.redis.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.entity.RedisOutBoxProcessor;
import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;
import com.team02.mopl.global.outbox.redis.repository.RedisCommandOutboxRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RedisOutboxServiceTest {

  @Mock RedisCommandOutboxRepository outboxRepository;
  @InjectMocks RedisOutboxService outboxService;

  @Nested
  class ProcessOutbox {
    @Test
    @DisplayName("지원하는 커맨드가 주어지면, 프로세스를 실행하고 변경사항을 저장한다")
    void success_shouldExecuteProcessAndSaveOutbox_whenSupportedCommandTypeProvided() {
      // given
      RedisOutBoxProcessor mockProcessor = mock(RedisOutBoxProcessor.class);
      ReflectionTestUtils.setField(outboxService, "processors", List.of(mockProcessor));
      given(mockProcessor.supports(any(CommandType.class))).willReturn(true);

      RedisCommandOutbox mockOutbox = mock(RedisCommandOutbox.class);
      given(mockOutbox.getCommandType()).willReturn(CommandType.DELETE_ALL_REFRESH_TOKEN);

      // when
      outboxService.processOutbox(mockOutbox);

      // then
      then(mockProcessor).should(times(1)).process(mockOutbox);
      then(mockOutbox).should(times(1)).markProcessed();
      then(outboxRepository).should(times(1)).save(mockOutbox);
    }

    @Test
    @DisplayName("매칭되는 프로세서가 없으면 예외를 던진다")
    void fail_shouldThrowException_whenNoMatchingProcessorFound() {
      // given
      RedisOutBoxProcessor mockProcessor = mock(RedisOutBoxProcessor.class);
      ReflectionTestUtils.setField(outboxService, "processors", List.of(mockProcessor));
      given(mockProcessor.supports(any(CommandType.class))).willReturn(false);

      RedisCommandOutbox mockOutbox = mock(RedisCommandOutbox.class);
      given(mockOutbox.getCommandType()).willReturn(CommandType.DELETE_ALL_REFRESH_TOKEN);

      // when & then
      assertThrows(IllegalStateException.class, () -> outboxService.processOutbox(mockOutbox));
    }
  }

  @Nested
  class DeleteAllOutboxDeletedAtIsNotNull {

    @Test
    @DisplayName("삭제된 건수가 제한 개수 이하면 한번만 쿼리 실행하고 종료한다")
    void success_shouldExecuteDeleteOnlyOnce_whenDeletedCountIsLessThanLimit() {
      // given
      given(outboxRepository.deleteTop1000ByProcessedTrue()).willReturn(400);

      // when
      outboxService.deleteAllOutboxProcessedIsTrue();

      // then
      then(outboxRepository).should(times(1)).deleteTop1000ByProcessedTrue();
    }

    @Test
    @DisplayName("삭제된 건수가 제한 개수보다 많으면 다 삭제할때까지 반복실행하고 종료한다")
    void success_shouldExecuteDeleteUntilRepeatedly_whenNoMoreDataToClean() {
      // given
      given(outboxRepository.deleteTop1000ByProcessedTrue()).willReturn(1000, 300);

      // when
      outboxService.deleteAllOutboxProcessedIsTrue();

      // then
      then(outboxRepository).should(times(2)).deleteTop1000ByProcessedTrue();
    }
  }

  @Nested
  class IncreateRetryCount {
    @Test
    @DisplayName("재시도횟수가 임계값을 초과하면 softDelete를 진행한다")
    void success_shouldSoftDeleteOutbox_whenRetryCountExceedsThreshold() {
      // given
      RedisCommandOutbox mockOutbox = mock(RedisCommandOutbox.class);
      given(mockOutbox.isFailedPermanently(anyInt())).willReturn(true);

      // when
      outboxService.increaseRetryCount(mockOutbox);

      // then
      then(mockOutbox).should(times(1)).delete();
      then(outboxRepository).should().save(mockOutbox);
    }

    @Test
    @DisplayName("재시도횟수가 임계값을 넘기지 않으면 softDelete를 진행하지 않는다")
    void success_shouldNotDontDelete_whenRetryCountDoesNotExceedThreshold() {
      // given
      RedisCommandOutbox mockOutbox = mock(RedisCommandOutbox.class);
      given(mockOutbox.isFailedPermanently(anyInt())).willReturn(false);

      // when
      outboxService.increaseRetryCount(mockOutbox);

      // then
      then(mockOutbox).should(never()).delete();
      then(outboxRepository).should().save(mockOutbox);
    }
  }
}
