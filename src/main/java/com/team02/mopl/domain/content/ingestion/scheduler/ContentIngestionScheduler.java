package com.team02.mopl.domain.content.ingestion.scheduler;

import com.team02.mopl.domain.content.ingestion.batch.ContentIngestionJobConfig;
import com.team02.mopl.domain.content.ingestion.batch.IngestionMode;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 콘텐츠 수집 배치를 주기 실행하는 스케줄러 (app.ingestion.scheduler.enabled=true일 때만 등록)
// - 두 주기: 일간 popular(DAILY) + 시간별 discover backfill(HOURLY)
// - IngestionRunLock을 모드별 키로 획득해 인스턴스 간 중복 실행을 방지 (획득 실패 시 이번 주기 skip)
//   DAILY(04:00)와 HOURLY(매시 정각)는 같은 시각에 겹치므로, 공유 키를 쓰면 한쪽이 통째로 스킵된다.
//   모드별 독립 키로 서로를 막지 않게 하되, 같은 모드의 멀티 인스턴스 동시 실행은 여전히 막는다
//   Spring Batch는 동일 JobInstance의 동시 실행만 막으므로 timestamp 파라미터 + 멀티 인스턴스 환경에서는 이 락이 필요
// - 수집 실패가 다음 주기 실행을 막지 않도록 예외는 로그만 남김 (결과 요약/알림은 IngestionJobListener 담당)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ingestion.scheduler.enabled", havingValue = "true")
public class ContentIngestionScheduler {

  private final JobLauncher jobLauncher;
  private final Job contentIngestionJob;
  private final IngestionRunLock runLock;
  private final IngestionSchedulerProperties properties;

  // 일간 popular 수집 (영화/드라마 popular + SportsDB 시즌)
  @Scheduled(cron = "${app.ingestion.scheduler.cron}", zone = "Asia/Seoul")
  public void collectDaily() {
    run(IngestionMode.DAILY, "일간 popular");
  }

  // 시간별 discover backfill (최신 -> 과거)
  @Scheduled(cron = "${app.ingestion.scheduler.backfill-cron}", zone = "Asia/Seoul")
  public void collectBackfill() {
    run(IngestionMode.HOURLY, "시간별 backfill");
  }

  private void run(IngestionMode mode, String label) {
    String token; // 락 해제 시 소유권 증명에 사용할 토큰
    try {
      token = runLock.tryAcquire(mode, properties.lockTtl()); // 모드별 키로 락 획득 시도
    } catch (Exception e) {
      // Redis 장애 시 fail-closed: 이번 주기는 건너뛰고 다음 cron에 재시도 (수집은 지연 허용 배치)
      log.error("수집 락 획득 실패(Redis 오류). 이번 주기 수집을 건너뜁니다. mode={}", label, e);
      return;
    }
    if (token == null) {
      // 다른 인스턴스 또는 다른 주기가 락을 이미 가지고 있는 경우
      log.info("콘텐츠 수집이 이미 실행 중입니다. 이번 주기를 건너뜁니다. mode={}", label);
      return;
    }
    log.info("스케줄 콘텐츠 수집 시작. mode={}", label);
    try {
      // 매 실행이 새 JobInstance가 되도록 timestamp를 식별 파라미터로 전달
      // (기본 JobLauncher는 동기 실행이므로 락이 배치 실행 내내 유지된다)
      JobParameters parameters =
          new JobParametersBuilder()
              .addLocalDateTime("runDateTime", LocalDateTime.now())
              .addString(ContentIngestionJobConfig.JOB_PARAM_MODE, mode.name())
              .toJobParameters();
      JobExecution execution = jobLauncher.run(contentIngestionJob, parameters);
      log.info(
          "스케줄 콘텐츠 수집 종료. mode={}, status={}, exitCode={}",
          label,
          execution.getStatus(),
          execution.getExitStatus().getExitCode());
    } catch (Exception e) {
      log.error("스케줄 콘텐츠 수집 실행 실패. mode={}", label, e);
    } finally {
      try {
        runLock.release(mode, token); // 락 해제가 실패해도 TTL이 최종 안전망
      } catch (Exception e) {
        log.warn("수집 락 해제 실패(Redis 오류). TTL 만료로 자동 해제됩니다.", e);
      }
    }
  }
}
