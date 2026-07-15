package com.team02.mopl.domain.content.ingestion;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 기동 시 1회 수집 배치를 실행하는 검증/수동 실행용 진입점(app.ingestion.run-on-startup=true일 때만 등록)
// 수집 실패가 애플리케이션 기동을 막지 않도록 예외는 로그만 남김
// 주기 실행은 ContentIngestionScheduler가 담당. 본 러너는 기동 시 수동 검증용으로 유지
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ingestion.run-on-startup", havingValue = "true")
public class ContentIngestionRunner implements ApplicationRunner {

  private final JobLauncher jobLauncher;
  private final Job contentIngestionJob;

  @Override
  public void run(ApplicationArguments args) {
    try {
      // 매 실행이 새 JobInstance가 되도록 timestamp를 식별 파라미터로 전달
      JobParameters parameters =
          new JobParametersBuilder()
              .addLocalDateTime("runDateTime", LocalDateTime.now())
              .toJobParameters();
      jobLauncher.run(contentIngestionJob, parameters);
    } catch (Exception e) {
      log.error("콘텐츠 수집 실행에 실패했습니다.", e);
    }
  }
}
