package com.team02.mopl.domain.content.ingestion;

import com.team02.mopl.domain.content.ingestion.batch.ContentIngestionJobConfig;
import com.team02.mopl.domain.content.ingestion.batch.IngestionMode;
import com.team02.mopl.domain.content.ingestion.scheduler.IngestionRunLock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 기동 시 1회 수집 배치를 실행하는 진입점(app.ingestion.run-on-startup=true일 때만 등록)
// - 다중 인스턴스가 동시에 기동하면 각 인스턴스가 중복 수집하므로, 스케줄러와 동일한 IngestionRunLock으로
//   한 인스턴스만 실행하도록 조율한다 (획득 실패 시 이번 기동은 skip). lock-ttl은 스케줄러와 동일 값을 공유하되,
//   IngestionSchedulerProperties는 scheduler.enabled=true일 때만 등록되는 조건부 빈이라 여기서는 @Value로 직접 읽는다
// - 수집 실패가 애플리케이션 기동을 막지 않도록 예외는 로그만 남김
// - 주기 실행은 ContentIngestionScheduler가 담당
@Slf4j
@Component
@ConditionalOnProperty(name = "app.ingestion.run-on-startup", havingValue = "true")
public class ContentIngestionRunner implements ApplicationRunner {

  private final JobLauncher jobLauncher;
  private final Job contentIngestionJob;
  private final IngestionRunLock runLock;
  private final Duration lockTtl;

  public ContentIngestionRunner(
      JobLauncher jobLauncher,
      Job contentIngestionJob,
      IngestionRunLock runLock,
      @Value("${app.ingestion.scheduler.lock-ttl}") Duration lockTtl) {
    this.jobLauncher = jobLauncher;
    this.contentIngestionJob = contentIngestionJob;
    this.runLock = runLock;
    this.lockTtl = lockTtl;
  }

  @Override
  public void run(ApplicationArguments args) {
    String token; // 락 해제 시 소유권 증명에 사용할 토큰
    try {
      token = runLock.tryAcquire(lockTtl); // 락 획득 시도
    } catch (Exception e) {
      // Redis 장애 시 fail-closed: 이번 기동 수집은 건너뛴다 (스케줄러/수동 실행으로 재시도 가능)
      log.error("수집 락 획득 실패(Redis 오류). 기동 시 수집을 건너뜁니다.", e);
      return;
    }
    if (token == null) {
      // 다른 인스턴스가 락을 이미 가지고 있는 경우
      log.info("콘텐츠 수집이 이미 실행 중입니다. 기동 시 수집을 건너뜁니다.");
      return;
    }
    log.info("기동 콘텐츠 수집 시작");
    try {
      // 매 실행이 새 JobInstance가 되도록 timestamp를 식별 파라미터로 전달
      // (기본 JobLauncher는 동기 실행이므로 락이 배치 실행 내내 유지된다)
      // 기동 수집은 DAILY(popular + SportsDB 시즌)로 시딩한다. 백필은 스케줄러(HOURLY)가 이어받는다.
      JobParameters parameters =
          new JobParametersBuilder()
              .addLocalDateTime("runDateTime", LocalDateTime.now())
              .addString(ContentIngestionJobConfig.JOB_PARAM_MODE, IngestionMode.DAILY.name())
              .toJobParameters();
      jobLauncher.run(contentIngestionJob, parameters);
    } catch (Exception e) {
      log.error("콘텐츠 수집 실행에 실패했습니다.", e);
    } finally {
      try {
        runLock.release(token); // 락 해제가 실패해도 TTL이 최종 안전망
      } catch (Exception e) {
        log.warn("수집 락 해제 실패(Redis 오류). TTL 만료로 자동 해제됩니다.", e);
      }
    }
  }
}
