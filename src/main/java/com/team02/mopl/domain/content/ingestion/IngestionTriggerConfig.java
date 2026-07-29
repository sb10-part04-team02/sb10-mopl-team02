package com.team02.mopl.domain.content.ingestion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 어드민 수동 수집 트리거가 배치를 백그라운드로 돌릴 때 쓰는 전용 실행기
// 기본 JobLauncher는 동기라 요청 스레드에서 실행하면 HTTP 응답이 배치가 끝날 때까지 막힌다.
// IngestionRunLock이 동시 실행을 1건으로 제한하므로 스레드 1개면 충분하다.
@Configuration
public class IngestionTriggerConfig {

  public static final String TRIGGER_EXECUTOR = "ingestionTriggerExecutor";

  @Bean(TRIGGER_EXECUTOR)
  public TaskExecutor ingestionTriggerExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ingestion-trigger-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(1);
    // 종료 시 진행 중인 수집 배치가 인터럽트로 끊기지 않도록 최대 60초 대기한다.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
  }
}
