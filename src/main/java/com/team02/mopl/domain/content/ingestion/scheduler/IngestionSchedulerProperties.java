package com.team02.mopl.domain.content.ingestion.scheduler;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

// 콘텐츠 수집 스케줄러 설정
// - cron: 일간 popular 수집 주기
// - backfillCron: 시간별 discover backfill 주기
@ConfigurationProperties(prefix = "app.ingestion.scheduler")
public record IngestionSchedulerProperties(
    boolean enabled, String cron, String backfillCron, Duration lockTtl) {

  public IngestionSchedulerProperties {
    if (enabled) {
      if (cron == null || cron.isBlank()) {
        throw new IllegalStateException("수집 스케줄러 cron은 필수입니다.");
      }
      if (!CronExpression.isValidExpression(cron)) {
        throw new IllegalStateException("수집 스케줄러 cron 형식이 올바르지 않습니다: " + cron);
      }
      if (backfillCron == null || backfillCron.isBlank()) {
        throw new IllegalStateException("수집 스케줄러 backfill-cron은 필수입니다.");
      }
      if (!CronExpression.isValidExpression(backfillCron)) {
        throw new IllegalStateException("수집 스케줄러 backfill-cron 형식이 올바르지 않습니다: " + backfillCron);
      }
      if (lockTtl == null || lockTtl.isNegative() || lockTtl.isZero()) {
        throw new IllegalStateException("수집 스케줄러 lock-ttl은 필수고 0보다 커야 합니다.");
      }
    }
  }
}
