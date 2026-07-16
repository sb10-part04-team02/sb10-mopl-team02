package com.team02.mopl.domain.content.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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

  @InjectMocks private ContentIngestionRunner runner;

  @Test
  @DisplayName("기동 시 runDateTime 파라미터로 수집 Job을 1회 실행한다")
  void run_launchesIngestionJob() throws Exception {
    // given - Job 실행 정상 동작
    // when
    runner.run(null);

    // then - 매 실행이 새 JobInstance가 되도록 timestamp 식별 파라미터가 전달되어야 한다
    ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
    then(jobLauncher).should().run(eq(contentIngestionJob), captor.capture());
    assertThat(captor.getValue().getLocalDateTime("runDateTime")).isNotNull();
  }

  @Test
  @DisplayName("Job 실행이 실패해도 예외를 전파하지 않는다 (애플리케이션 기동 보호)")
  void run_whenLaunchFails_doesNotPropagate() throws Exception {
    // given
    given(jobLauncher.run(eq(contentIngestionJob), any(JobParameters.class)))
        .willThrow(new RuntimeException("실행 실패"));

    // when & then
    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
  }
}
