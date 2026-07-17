package com.team02.mopl.domain.content.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.batch.ContentIngestionJobConfig;
import com.team02.mopl.domain.content.ingestion.exception.IngestionAlreadyRunningException;
import com.team02.mopl.domain.content.ingestion.scheduler.IngestionRunLock;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.core.task.TaskExecutor;

@ExtendWith(MockitoExtension.class)
class ContentIngestionTriggerTest {

  @Mock private JobLauncher jobLauncher;
  @Mock private Job contentIngestionJob;
  @Mock private IngestionRunLock runLock;

  // 백그라운드로 넘긴 작업을 그대로 실행해 검증한다 (실제 운영에서는 별도 스레드)
  private final TaskExecutor directExecutor = Runnable::run;

  private ContentIngestionTrigger trigger() {
    return new ContentIngestionTrigger(jobLauncher, contentIngestionJob, runLock, directExecutor);
  }

  private JobParameters capturedParameters() throws Exception {
    ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
    then(jobLauncher).should().run(eq(contentIngestionJob), captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("수집이 이미 실행 중이면(락 점유) 배치를 실행하지 않고 409 예외로 거절한다")
  void trigger_whenAlreadyRunning_rejectsWithoutRunningJob() {
    // given - 스케줄 실행이나 다른 인스턴스가 이미 락을 쥔 상황
    given(runLock.tryAcquire(any())).willReturn(null);

    // when & then
    assertThatThrownBy(() -> trigger().trigger(Set.of(ContentSource.TMDB)))
        .isInstanceOf(IngestionAlreadyRunningException.class);
    then(jobLauncher).shouldHaveNoInteractions();
    then(runLock).should(org.mockito.Mockito.never()).release(any());
  }

  @Test
  @DisplayName("선택한 소스만 sources 파라미터로 넘겨 배치를 실행하고, 끝나면 락을 해제한다")
  void trigger_runsJobWithSelectedSources() throws Exception {
    // given
    given(runLock.tryAcquire(any())).willReturn("token");

    // when
    Set<ContentSource> triggered = trigger().trigger(Set.of(ContentSource.SPORTS_DB));

    // then
    assertThat(triggered).containsExactly(ContentSource.SPORTS_DB);
    assertThat(capturedParameters().getString(ContentIngestionJobConfig.JOB_PARAM_SOURCES))
        .isEqualTo("SPORTS_DB");
    then(runLock).should().release("token");
  }

  @Test
  @DisplayName("소스를 지정하지 않으면 전체 소스를 수집한다")
  void trigger_whenSourcesOmitted_collectsAllSources() throws Exception {
    // given
    given(runLock.tryAcquire(any())).willReturn("token");

    // when
    Set<ContentSource> triggered = trigger().trigger(null);

    // then
    assertThat(triggered).containsExactlyInAnyOrder(ContentSource.values());
    assertThat(capturedParameters().getString(ContentIngestionJobConfig.JOB_PARAM_SOURCES))
        .contains("TMDB", "SPORTS_DB");
    then(runLock).should().release("token");
  }

  @Test
  @DisplayName("매 실행이 새 JobInstance가 되도록 runDateTime을 식별 파라미터로 넘긴다")
  void trigger_passesRunDateTimeParameter() throws Exception {
    // given
    given(runLock.tryAcquire(any())).willReturn("token");

    // when
    trigger().trigger(Set.of(ContentSource.TMDB));

    // then
    assertThat(capturedParameters().getParameters()).containsKey("runDateTime");
  }

  @Test
  @DisplayName("배치 실행이 실패해도 예외를 호출자에게 전파하지 않고 락은 해제한다")
  void trigger_whenJobFails_releasesLockAndSwallows() throws Exception {
    // given - 이미 202로 응답한 뒤이므로 호출자에게 돌려줄 곳이 없다
    given(runLock.tryAcquire(any())).willReturn("token");
    given(jobLauncher.run(any(), any())).willThrow(new IllegalStateException("실행 실패"));

    // when
    Set<ContentSource> triggered = trigger().trigger(Set.of(ContentSource.TMDB));

    // then - 락이 TTL까지 남아 다음 수집을 막으면 안 된다
    assertThat(triggered).containsExactly(ContentSource.TMDB);
    then(runLock).should().release("token");
  }

  @Test
  @DisplayName("실행기에 작업을 넘기지 못하면 락을 해제한 뒤 예외를 전파한다")
  void trigger_whenExecutorRejects_releasesLock() {
    // given - 실행기 큐가 가득 찬 상황
    given(runLock.tryAcquire(any())).willReturn("token");
    TaskExecutor rejectingExecutor =
        task -> {
          throw new java.util.concurrent.RejectedExecutionException("큐 가득");
        };
    ContentIngestionTrigger trigger =
        new ContentIngestionTrigger(jobLauncher, contentIngestionJob, runLock, rejectingExecutor);

    // when & then
    assertThatThrownBy(() -> trigger.trigger(Set.of(ContentSource.TMDB)))
        .isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
    then(runLock).should().release("token");
  }
}
