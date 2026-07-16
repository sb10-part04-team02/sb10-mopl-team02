package com.team02.mopl.domain.content.ingestion.scheduler;

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
// - IngestionRunLock으로 인스턴스 간 중복 실행을 방지한다 (획득 실패 시 이번 주기 skip)
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

  @Scheduled(cron = "${app.ingestion.scheduler.cron}", zone = "Asia/Seoul")
  public void collectAll() {
    String token; // 락 해제 시 소유권 증명에 사용할 토큰
    try {
      token = runLock.tryAcquire(properties.lockTtl()); // 락 획득 시도
    } catch (Exception e) {
      // Redis 장애 시 fail-closed: 이번 주기는 건너뛰고 다음 cron에 재시도 (수집은 지연 허용 배치)
      log.error("수집 락 획득 실패(Redis 오류). 이번 주기 수집을 건너뜁니다.", e);
      return;
    }

    if (token == null) {
      // 다른 인스턴스가 락을 이미 가지고 있는 경우
      log.info("콘텐츠 수집이 이미 실행 중입니다. 이번 주기를 건너뜁니다.");
      return;
    }

    log.info("스케줄 콘텐츠 수집 시작");
    try {
      // 매 실행이 새 JobInstance가 되도록 timestamp를 식별 파라미터로 전달
      // (기본 JobLauncher는 동기 실행이므로 락이 배치 실행 내내 유지된다)
      JobParameters parameters =
          new JobParametersBuilder()
              .addLocalDateTime("runDateTime", LocalDateTime.now())
              .toJobParameters();
      JobExecution execution = jobLauncher.run(contentIngestionJob, parameters);
      log.info(
          "스케줄 콘텐츠 수집 종료. status={}, exitCode={}",
          execution.getStatus(),
          execution.getExitStatus().getExitCode());
    } catch (Exception e) {
      log.error("스케줄 콘텐츠 수집 실행 실패.", e);
    } finally {
      try {
        runLock.release(token); // 락 해제가 실패해도 TTL이 최종 안전망
      } catch (Exception e) {
        log.warn("수집 락 해제 실패(Redis 오류). TTL 만료로 자동 해제됩니다.", e);
      }
    }
  }
}
