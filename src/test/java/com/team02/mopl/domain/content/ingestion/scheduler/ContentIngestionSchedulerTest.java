package com.team02.mopl.domain.content.ingestion.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.team02.mopl.domain.content.ingestion.batch.IngestionMode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
class ContentIngestionSchedulerTest {

  private static final String TOKEN = "lock-token";

  @Mock private JobLauncher jobLauncher;
  @Mock private Job contentIngestionJob;
  @Mock private IngestionRunLock runLock;

  private ContentIngestionScheduler scheduler;

  @BeforeEach
  void setUp() {
    IngestionSchedulerProperties properties =
        new IngestionSchedulerProperties(
            true, "0 0 4 * * *", "0 0 * * * *", Duration.ofMinutes(30));
    scheduler =
        new ContentIngestionScheduler(jobLauncher, contentIngestionJob, runLock, properties);
  }

  @Test
  @DisplayName("락 획득에 성공하면 runDateTime 파라미터로 수집 Job을 실행하고 락을 해제한다")
  void collectAll_whenLockAcquired_launchesJobAndReleases() throws Exception {
    // given - 락 획득이 성공해서 TOKEN을 돌려주는 상황
    given(runLock.tryAcquire(any(IngestionMode.class), any())).willReturn(TOKEN);
    given(jobLauncher.run(eq(contentIngestionJob), any(JobParameters.class)))
        .willReturn(completedExecution());

    // when
    scheduler.collectDaily();

    // then - 매 실행이 새 JobInstance가 되도록 timestamp 식별 파라미터가 전달되어야 한다
    ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
    then(jobLauncher).should().run(eq(contentIngestionJob), captor.capture());
    assertThat(captor.getValue().getLocalDateTime("runDateTime")).isNotNull();
    // DAILY/HOURLY 락 경합을 막도록 모드별 키로 획득/해제해야 한다
    then(runLock).should().tryAcquire(eq(IngestionMode.DAILY), any());
    then(runLock).should().release(eq(IngestionMode.DAILY), eq(TOKEN)); // 락 해제 호출 확인
  }

  @Test
  @DisplayName("일간/시간별 수집은 서로 다른 모드 키로 락을 잡아 같은 시각에 겹쳐도 서로를 막지 않는다")
  void collectDailyAndBackfill_useSeparateModeLockKeys() throws Exception {
    // given
    given(runLock.tryAcquire(any(IngestionMode.class), any())).willReturn(TOKEN);
    given(jobLauncher.run(eq(contentIngestionJob), any(JobParameters.class)))
        .willReturn(completedExecution());

    // when - 두 스케줄이 같은 시각에 트리거되는 상황
    scheduler.collectDaily();
    scheduler.collectBackfill();

    // then - 각 모드가 자기 키로 독립 획득/해제한다
    then(runLock).should().tryAcquire(eq(IngestionMode.DAILY), any());
    then(runLock).should().tryAcquire(eq(IngestionMode.HOURLY), any());
    then(runLock).should().release(eq(IngestionMode.DAILY), eq(TOKEN));
    then(runLock).should().release(eq(IngestionMode.HOURLY), eq(TOKEN));
  }

  @Test
  @DisplayName("락 획득에 실패하면(이미 실행 중) 수집을 건너뛴다")
  void collectAll_whenLockNotAcquired_skips() {
    // given
    given(runLock.tryAcquire(any(IngestionMode.class), any())).willReturn(null);

    // when
    scheduler.collectDaily();

    // then
    then(jobLauncher).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("Job 실행이 실패해도 예외를 전파하지 않고 락은 해제한다 (스케줄 지속 보장)")
  void collectAll_whenLaunchFails_doesNotPropagateAndReleases() throws Exception {
    // given
    given(runLock.tryAcquire(any(IngestionMode.class), any())).willReturn(TOKEN);
    given(jobLauncher.run(eq(contentIngestionJob), any(JobParameters.class)))
        .willThrow(new RuntimeException("실행 실패"));

    // when & then
    assertThatCode(() -> scheduler.collectDaily()).doesNotThrowAnyException();
    then(runLock).should().release(eq(IngestionMode.DAILY), eq(TOKEN));
  }

  @Test
  @DisplayName("락 해제 중 Redis 오류가 나도 예외를 전파하지 않는다 (정상 완료 보장, TTL이 안전망)")
  void collectAll_whenReleaseFails_doesNotPropagate() throws Exception {
    // given - 수집은 성공했지만 락 해제에서 Redis 오류가 발생하는 상황
    given(runLock.tryAcquire(any(IngestionMode.class), any())).willReturn(TOKEN);
    given(jobLauncher.run(eq(contentIngestionJob), any(JobParameters.class)))
        .willReturn(completedExecution());
    willThrow(new RuntimeException("Redis 오류"))
        .given(runLock)
        .release(any(IngestionMode.class), eq(TOKEN));

    // when & then - 해제 실패가 성공한 수집을 예외로 뒤바꾸지 않아야 한다
    assertThatCode(() -> scheduler.collectDaily()).doesNotThrowAnyException();
    then(jobLauncher).should().run(eq(contentIngestionJob), any(JobParameters.class));
  }

  @Test
  @DisplayName("락 획득 중 Redis 오류가 나면 예외를 전파하지 않고 수집을 건너뛴다")
  void collectAll_whenLockAcquireFails_doesNotPropagateAndSkips() {
    // given
    given(runLock.tryAcquire(any(IngestionMode.class), any()))
        .willThrow(new RuntimeException("Redis 오류"));

    // when & then
    assertThatCode(() -> scheduler.collectDaily()).doesNotThrowAnyException();
    then(jobLauncher).shouldHaveNoInteractions();
  }

  private JobExecution completedExecution() {
    JobExecution execution = new JobExecution(1L);
    execution.setStatus(BatchStatus.COMPLETED);
    execution.setExitStatus(ExitStatus.COMPLETED);
    return execution;
  }
}
