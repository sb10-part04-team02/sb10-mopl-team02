package com.team02.mopl.domain.content.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.content.ingestion.scheduler.IngestionRunLock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

@ExtendWith(MockitoExtension.class)
class ContentIngestionRunnerTest {

  @Mock private JobLauncher jobLauncher;
  @Mock private Job contentIngestionJob;
  @Mock private IngestionRunLock runLock;

  @InjectMocks private ContentIngestionRunner runner;

  @Test
  @DisplayName("락 획득 성공 시 runDateTime 파라미터로 수집 Job을 1회 실행하고 락을 해제한다")
  void run_whenLockAcquired_launchesJobAndReleasesLock() throws Exception {
    // given
    String token = "token";
    given(runLock.tryAcquire(any())).willReturn(token);

    // when
    runner.run(null);

    // then - 매 실행이 새 JobInstance가 되도록 timestamp 식별 파라미터가 전달되어야 한다
    ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
    then(jobLauncher).should().run(eq(contentIngestionJob), captor.capture());
    assertThat(captor.getValue().getLocalDateTime("runDateTime")).isNotNull();
    then(runLock).should().release(token); // 실행 후 락 해제
  }

  @Test
  @DisplayName("락 획득 실패(다른 인스턴스 실행 중) 시 Job을 실행하지 않고 락도 해제하지 않는다")
  void run_whenLockUnavailable_skipsJob() throws Exception {
    // given - 다른 인스턴스가 이미 락을 보유
    given(runLock.tryAcquire(any())).willReturn(null);

    // when
    runner.run(null);

    // then - 내가 잡은 락이 없으므로 실행도 해제도 하지 않는다
    then(jobLauncher).should(never()).run(any(), any());
    then(runLock).should(never()).release(any());
  }

  @Test
  @DisplayName("Job 실행이 실패해도 예외를 전파하지 않고 락을 해제한다 (애플리케이션 기동 보호)")
  void run_whenLaunchFails_doesNotPropagateAndReleasesLock() throws Exception {
    // given
    String token = "token";
    given(runLock.tryAcquire(any())).willReturn(token);
    given(jobLauncher.run(eq(contentIngestionJob), any(JobParameters.class)))
        .willThrow(new RuntimeException("실행 실패"));

    // when & then - 실패해도 finally에서 락은 해제된다
    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    then(runLock).should().release(token);
  }

  @Test
  @DisplayName("동시 기동 시 락을 획득한 인스턴스만 수집 Job을 실행하고, 나머지는 건너뛴다")
  void run_concurrentStartup_onlyLockHolderRunsJob() throws Exception {
    // given - 두 인스턴스가 공유 락을 경합: 먼저 잡은 쪽만 토큰, 나머지는 null
    String token = "token";
    given(runLock.tryAcquire(any())).willReturn(token, (String) null);
    ContentIngestionRunner holder =
        new ContentIngestionRunner(
            jobLauncher, contentIngestionJob, runLock, Duration.ofMinutes(30));
    ContentIngestionRunner loser =
        new ContentIngestionRunner(
            jobLauncher, contentIngestionJob, runLock, Duration.ofMinutes(30));

    // when - 두 인스턴스가 각자 기동
    holder.run(null);
    loser.run(null);

    // then - 두 번 경합했지만 Job은 홀더에서 1회만 실행되고, 홀더의 토큰만 해제된다
    then(runLock).should(times(2)).tryAcquire(any());
    then(jobLauncher).should(times(1)).run(eq(contentIngestionJob), any(JobParameters.class));
    then(runLock).should(times(1)).release(token);
  }
}
