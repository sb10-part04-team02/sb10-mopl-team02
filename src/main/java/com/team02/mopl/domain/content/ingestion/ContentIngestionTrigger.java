package com.team02.mopl.domain.content.ingestion;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.batch.ContentIngestionJobConfig;
import com.team02.mopl.domain.content.ingestion.exception.IngestionAlreadyRunningException;
import com.team02.mopl.domain.content.ingestion.scheduler.IngestionRunLock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

// 어드민 수동 수집의 진입점. 락을 잡은 즉시 반환하고(컨트롤러는 202) 배치는 백그라운드에서 실행한다
// - 스케줄러와 같은 IngestionRunLock을 공유하므로 스케줄 실행/다른 인스턴스/직전 수동 요청과 겹치지 않는다
//   (이미 실행 중이면 IngestionAlreadyRunningException -> 409)
// - 기본 JobLauncher는 동기라서 전용 실행기 스레드에서 run()을 호출하고 그 스레드에서 락을 해제한다.
//   전역 JobLauncher를 비동기로 바꾸면 스케줄러의 "run() 반환 = 수집 종료" 전제가 깨지므로 건드리지 않는다.
// - lock-ttl은 스케줄러/기동 러너와 같은 값을 공유
@Slf4j
@Service
public class ContentIngestionTrigger {

  private final JobLauncher jobLauncher;
  private final Job contentIngestionJob;
  private final IngestionRunLock runLock;
  private final TaskExecutor triggerExecutor;
  private final Duration lockTtl;

  public ContentIngestionTrigger(
      JobLauncher jobLauncher,
      Job contentIngestionJob,
      IngestionRunLock runLock,
      @Qualifier(IngestionTriggerConfig.TRIGGER_EXECUTOR) TaskExecutor triggerExecutor,
      @Value("${app.ingestion.scheduler.lock-ttl}") Duration lockTtl) {
    this.jobLauncher = jobLauncher;
    this.contentIngestionJob = contentIngestionJob;
    this.runLock = runLock;
    this.triggerExecutor = triggerExecutor;
    this.lockTtl = lockTtl;
  }

  // 수집을 시작하고 실제 대상 소스를 반환한다. requested가 비어 있으면 전체 소스를 수집한다
  public Set<ContentSource> trigger(Set<ContentSource> requested) {
    Set<ContentSource> sources =
        (requested == null || requested.isEmpty())
            ? EnumSet.allOf(ContentSource.class)
            : EnumSet.copyOf(requested);

    // Redis 장애는 감추지 않고 그대로 전파해 500으로 드러낸다 (수동 트리거는 즉시 재시도가 가능하다)
    String token = runLock.tryAcquire(lockTtl);
    if (token == null) {
      throw new IngestionAlreadyRunningException();
    }
    try {
      triggerExecutor.execute(() -> runJob(sources, token));
    } catch (RuntimeException e) {
      release(token); // 실행기에 넘기지 못했으면 락을 TTL까지 물고 있으면 안 된다
      throw e;
    }
    log.info("수동 콘텐츠 수집을 시작했습니다. sources={}", sources);
    return sources;
  }

  private void runJob(Set<ContentSource> sources, String token) {
    try {
      // 매 실행이 새 JobInstance가 되도록 timestamp를 식별 파라미터로 전달 (스케줄러와 동일)
      JobParameters parameters =
          new JobParametersBuilder()
              .addLocalDateTime("runDateTime", LocalDateTime.now())
              .addString(
                  ContentIngestionJobConfig.JOB_PARAM_SOURCES,
                  sources.stream().map(Enum::name).collect(Collectors.joining(",")))
              .toJobParameters();
      jobLauncher.run(contentIngestionJob, parameters);
    } catch (Exception e) {
      // 이미 202로 응답한 뒤라 호출자에게 돌려줄 곳이 없다. 결과 요약/알림은 IngestionJobListener가 담당
      log.error("수동 콘텐츠 수집 실행 실패. sources={}", sources, e);
    } finally {
      release(token);
    }
  }

  private void release(String token) {
    try {
      runLock.release(token); // 해제가 실패해도 TTL이 최종 안전망
    } catch (Exception e) {
      log.warn("수집 락 해제 실패(Redis 오류). TTL 만료로 자동 해제됩니다.", e);
    }
  }
}
