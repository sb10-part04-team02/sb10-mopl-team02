package com.team02.mopl.domain.content.ingestion.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IngestionSchedulerPropertiesTest {

  private static final String VALID_CRON = "0 0 4 * * *";
  private static final Duration VALID_TTL = Duration.ofMinutes(30);

  @Test
  @DisplayName("유효한 설정이면 정상 생성된다")
  void create_withValidValues_succeeds() {
    assertThatCode(() -> new IngestionSchedulerProperties(true, VALID_CRON, VALID_TTL))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("enabled=true일 때 cron이 비어 있으면 기동 시점에 실패한다")
  void create_withBlankCron_throws() {
    assertThatThrownBy(() -> new IngestionSchedulerProperties(true, " ", VALID_TTL))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("enabled=true일 때 cron 형식이 잘못되면 기동 시점에 실패한다")
  void create_withInvalidCron_throws() {
    assertThatThrownBy(() -> new IngestionSchedulerProperties(true, "매일 새벽 4시", VALID_TTL))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("enabled=true일 때 lock-ttl이 없거나 0 이하면 기동 시점에 실패한다")
  void create_withInvalidLockTtl_throws() {
    assertThatThrownBy(() -> new IngestionSchedulerProperties(true, VALID_CRON, null))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> new IngestionSchedulerProperties(true, VALID_CRON, Duration.ZERO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("enabled=false면 나머지 값이 비어 있어도 통과한다 (기본 프로파일 안전)")
  void create_whenDisabled_skipsValidation() {
    assertThatCode(() -> new IngestionSchedulerProperties(false, null, null))
        .doesNotThrowAnyException();
  }
}
