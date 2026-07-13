package com.team02.mopl.global.outbox.redis.scheduler;

import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.repository.RedisCommandOutboxRepository;
import com.team02.mopl.global.outbox.redis.service.RedisOutboxService;
import com.team02.mopl.global.redis.RedisLockManager;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisOutboxScheduler {

  private final RedisCommandOutboxRepository outboxRepository;
  private final RedisLockManager lockManager;
  private final RedisOutboxService outboxService;

  private static final String RETRY_FAIL_COMMAND_KEY = "RetryFailRedisCommand";
  private static final String OUTBOX_CLEANUP_KEY = "OutboxCleanup";

  @Scheduled(fixedDelayString = "${app.redis.outbox.retry-delay}")
  public void retryFailRedisCommands() {
    log.debug("[Outbox] Fail RedisCommand 재시작");

    String lockValue = lockManager.acquireLock(RETRY_FAIL_COMMAND_KEY, Duration.ofMinutes(1));
    if (lockValue == null) {
      log.debug("락 획득에 실패했습니다. 다른 앱이 진행중이므로 패스합니다.");
      return;
    }

    try {
      List<RedisCommandOutbox> outboxes =
          outboxRepository.findAllByDeletedAtIsNullOrderByCreatedAtAsc();

      for (RedisCommandOutbox outbox : outboxes) {
        try {
          outboxService.processOutbox(outbox);
        } catch (Exception e) {
          log.warn(
              "[Outbox] 명령 실패. 나중에 다시 시작합니다: target={}, commandType={}, reason={}",
              outbox.getTarget(),
              outbox.getCommandType(),
              e.getMessage());
        }
      }
    } finally {
      lockManager.releaseLock(RETRY_FAIL_COMMAND_KEY, lockValue);
    }
  }

  @Scheduled(cron = "${app.redis.outbox.clean-up}")
  public void cleanUp() {
    log.debug("[Outbox] 소프트삭제 데이터 물리청소 스케줄러 시작");

    String lockValue = lockManager.acquireLock(OUTBOX_CLEANUP_KEY, Duration.ofMinutes(10));
    if (lockValue == null) {
      log.debug("락 획득에 실패했습니다. 다른 앱이 진행중이므로 패스합니다.");
      return;
    }

    try {
      outboxService.deleteAllOutboxDeletedAtIsNotNull();
      log.info("[Outbox] 소프트삭제된 outbox 물리삭제 성공");
    } finally {
      lockManager.releaseLock(OUTBOX_CLEANUP_KEY, lockValue);
    }
  }
}
