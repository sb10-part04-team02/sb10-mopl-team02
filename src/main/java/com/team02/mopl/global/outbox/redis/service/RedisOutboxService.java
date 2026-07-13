package com.team02.mopl.global.outbox.redis.service;

import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.entity.RedisOutBoxProcessor;
import com.team02.mopl.global.outbox.redis.repository.RedisCommandOutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RedisOutboxService {
  private final RedisCommandOutboxRepository outboxRepository;
  private final List<RedisOutBoxProcessor> processors;
  private static final Integer DELETE_LIMIT_COUNT = 1000;

  @Value("${app.redis.outbox.maximum-retry-threshold}")
  private int retryCountThreshold;

  @Transactional
  public void processOutbox(RedisCommandOutbox outbox) {
    RedisOutBoxProcessor processor =
        processors.stream()
            .filter(p -> p.supports(outbox.getCommandType()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("[Outbox] 지원하지 않는 커맨드입니다"));

    processor.process(outbox);
    outbox.markProcessed();
    outboxRepository.save(outbox); // 준영속 상태라 명시적 save 진행
  }

  @Transactional
  public void deleteAllOutboxProcessedIsTrue() {
    int deletedCount = 0;
    do {
      // 1000건 반복 삭제
      deletedCount = outboxRepository.deleteTop1000ByProcessedTrue();
    } while (deletedCount >= DELETE_LIMIT_COUNT);
  }

  @Transactional
  public void increaseRetryCount(RedisCommandOutbox outbox) {
    outbox.incrementRetryCount();

    if (outbox.isFailedPermanently(retryCountThreshold)) {
      outbox.delete();
    }
    outboxRepository.save(outbox); // 준영속 상태라 명시적 save 진행
  }
}
