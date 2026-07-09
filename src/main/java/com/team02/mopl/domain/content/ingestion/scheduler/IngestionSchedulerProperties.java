package com.team02.mopl.domain.content.ingestion.scheduler;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

// 콘텐츠 수집 스케줄러 설정
// - cron 필드는 기동 시점 fail-fast 검증용. 실제 트리거는 @Scheduled placeholder가 같은 키를 직접 읽음
// - enabled=false(기본값)면 스케줄러 빈 자체가 등록되지 않으므로 나머지 값은 검증하지 않음
@ConfigurationProperties(prefix = "app.ingestion.scheduler")
public record IngestionSchedulerProperties(boolean enabled, String cron, Duration lockTtl) {

  public IngestionSchedulerProperties {
    if (enabled) {
      if (cron == null || cron.isBlank()) {
        throw new IllegalStateException("수집 스케줄러 cron은 필수입니다.");
      }
      if (!CronExpression.isValidExpression(cron)) {
        throw new IllegalStateException("수집 스케줄러 cron 형식이 올바르지 않습니다: " + cron);
      }
      if (lockTtl == null || lockTtl.isNegative() || lockTtl.isZero()) {
        throw new IllegalStateException("수집 스케줄러 lock-ttl은 필수고 0보다 커야 합니다.");
      }
    }
  }
}
