package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.global.alert.DiscordNotifier;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

// 수집 Job의 최종 상태 판정과 운영 가시성(요약 로그 + 디스코드 알림)을 담당
// - Job flow가 소스 간 격리를 위해 스텝 실패에도 다음 스텝으로 진행하므로,
//   FAILED 스텝이 있으면 Job 상태를 FAILED로 강등해 부분 실패가 COMPLETED로 가려지지 않게 한다
// - Job 실패 또는 항목 단위 실패(skip)가 1건이라도 있으면 디스코드로 알린다
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionJobListener implements JobExecutionListener {

  private final DiscordNotifier discordNotifier;

  @Override
  public void afterJob(JobExecution jobExecution) {
    demoteIfAnyStepFailed(jobExecution);

    Collection<StepExecution> steps = jobExecution.getStepExecutions();
    long read = steps.stream().mapToLong(StepExecution::getReadCount).sum(); // 읽어들인 항목 총수
    long failed = steps.stream().mapToLong(StepExecution::getSkipCount).sum(); // skip된 항목 수
    int inserted = sumContext(steps, ContentUpsertItemWriter.CONTEXT_KEY_INSERTED); //
    int updated = sumContext(steps, ContentUpsertItemWriter.CONTEXT_KEY_UPDATED);
    int skipped = sumContext(steps, ContentUpsertItemWriter.CONTEXT_KEY_SKIPPED);
    long elapsedMs = elapsedMs(jobExecution); // Job 소요 시간

    if (jobExecution.getStatus() == BatchStatus.FAILED) {
      log.error(
          "콘텐츠 수집 배치 실패. read={}, inserted={}, updated={}, skipped={}, failed={}, elapsedMs={}",
          read,
          inserted,
          updated,
          skipped,
          failed,
          elapsedMs);
    } else if (failed > 0) { // Job 자체는 완료됐지만 항목 일부가 skip된 경우로, warn으로 일부 항목 실패를 알림
      log.warn(
          "콘텐츠 수집 배치 완료(일부 항목 실패). read={}, inserted={}, updated={}, skipped={}, failed={}, elapsedMs={}",
          read,
          inserted,
          updated,
          skipped,
          failed,
          elapsedMs);
    } else {
      log.info(
          "콘텐츠 수집 배치 완료. read={}, inserted={}, updated={}, skipped={}, failed={}, elapsedMs={}",
          read,
          inserted,
          updated,
          skipped,
          failed,
          elapsedMs);
      return; // 정상 완료는 알림 없음
    }
    discordNotifier.notify(buildAlertMessage(jobExecution, elapsedMs)); // 디스코드 알림 발송
  }

  // 스탭 중 하나라도 실패했는데, Job이 COMPLETED로 끝났다면 강제로 FAILED로 내린다.
  // flow의 on("*") 전이가 스텝 실패를 삼켜 Job이 COMPLETED로 끝나는 것을 보정
  // 실패한 스텝을 지나 flow가 계속 진행되면 상태가 FAILED가 아닌 ABANDONED로 남으므로 ExitStatus로 판정한다
  private void demoteIfAnyStepFailed(JobExecution jobExecution) {
    boolean anyStepFailed = jobExecution.getStepExecutions().stream().anyMatch(this::isFailed);
    if (anyStepFailed && jobExecution.getStatus() == BatchStatus.COMPLETED) {
      jobExecution.setStatus(BatchStatus.FAILED);
      jobExecution.setExitStatus(ExitStatus.FAILED);
    }
  }

  // BatchStatus가 FAILED인 경우 + ExitStatus의 코드가 "FAILED"인 경우 (-> 실패한 스탭 잡아내기 위함)
  private boolean isFailed(StepExecution step) {
    return step.getStatus() == BatchStatus.FAILED
        || ExitStatus.FAILED.getExitCode().equals(step.getExitStatus().getExitCode());
  }

  // 디스코드 알림 메시지 조립
  private String buildAlertMessage(JobExecution jobExecution, long elapsedMs) {
    StringBuilder message = new StringBuilder();
    message
        .append("[콘텐츠 수집 배치] 상태=")
        .append(jobExecution.getStatus())
        .append(", 소요=")
        .append(elapsedMs)
        .append("ms");
    for (StepExecution step : jobExecution.getStepExecutions()) {
      message
          .append("\n- ")
          .append(step.getStepName())
          .append(": ")
          .append(step.getExitStatus().getExitCode()) // 실패 후 진행 시 상태는 ABANDONED라 ExitStatus가 명확
          .append(", read=")
          .append(step.getReadCount())
          .append(", inserted=")
          .append(
              step.getExecutionContext().getInt(ContentUpsertItemWriter.CONTEXT_KEY_INSERTED, 0))
          .append(", updated=")
          .append(step.getExecutionContext().getInt(ContentUpsertItemWriter.CONTEXT_KEY_UPDATED, 0))
          .append(", skipped=")
          .append(step.getExecutionContext().getInt(ContentUpsertItemWriter.CONTEXT_KEY_SKIPPED, 0))
          .append(", failed=")
          .append(step.getSkipCount());
    }
    List<Throwable> failures = jobExecution.getAllFailureExceptions();
    if (!failures.isEmpty()) {
      message.append("\n예외: ").append(failures.get(0)); // 상세는 서버 로그 참조
    }
    return message.toString();
  }

  // ExecutionContext에서 주어진 키의 int를 모두 합산
  private int sumContext(Collection<StepExecution> steps, String key) {
    return steps.stream().mapToInt(step -> step.getExecutionContext().getInt(key, 0)).sum();
  }

  // 시작/종료 시각으로 소요 시간 계산
  private long elapsedMs(JobExecution jobExecution) {
    LocalDateTime start = jobExecution.getStartTime();
    LocalDateTime end = jobExecution.getEndTime();
    if (start == null || end == null) {
      return 0;
    }
    return Duration.between(start, end).toMillis();
  }
}
