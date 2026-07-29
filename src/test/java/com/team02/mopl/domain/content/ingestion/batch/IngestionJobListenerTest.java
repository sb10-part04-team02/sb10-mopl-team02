package com.team02.mopl.domain.content.ingestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.global.alert.DiscordNotifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;

@ExtendWith(MockitoExtension.class)
class IngestionJobListenerTest {

  @Mock private DiscordNotifier discordNotifier;

  @InjectMocks private IngestionJobListener listener;

  @Test
  @DisplayName("모든 스텝이 성공하고 skip이 없으면 상태를 유지하고 알림을 보내지 않는다")
  void afterJob_whenCleanRun_doesNotNotify() {
    // given
    JobExecution jobExecution = jobExecution();
    completedStep(jobExecution, "tmdbStep", 0);
    completedStep(jobExecution, "sportsDbStep", 0);

    // when
    listener.afterJob(jobExecution);

    // then
    assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    then(discordNotifier).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("FAILED 스텝이 있으면 Job 상태를 FAILED로 강등하고 알림을 보낸다 (flow의 부분 실패 가림 보정)")
  void afterJob_whenAnyStepFailed_demotesToFailedAndNotifies() {
    // given - tmdbStep 실패, sportsDbStep은 성공했지만 flow 전이로 Job은 COMPLETED로 끝난 상황
    // 실패한 스텝을 지나 flow가 진행되면 스텝 상태는 ABANDONED로 남고 실패는 ExitStatus에 남는다
    JobExecution jobExecution = jobExecution();
    StepExecution failedStep = jobExecution.createStepExecution("tmdbStep");
    failedStep.setStatus(BatchStatus.ABANDONED);
    failedStep.setExitStatus(ExitStatus.FAILED);
    failedStep.addFailureException(new RuntimeException("TMDB 장애"));
    completedStep(jobExecution, "sportsDbStep", 0);

    // when
    listener.afterJob(jobExecution);

    // then
    assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(jobExecution.getExitStatus().getExitCode())
        .isEqualTo(ExitStatus.FAILED.getExitCode());
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    then(discordNotifier).should().notify(captor.capture());
    assertThat(captor.getValue())
        .contains("상태=FAILED")
        .contains("tmdbStep")
        .contains("sportsDbStep")
        .contains("TMDB 장애");
  }

  @Test
  @DisplayName("항목 단위 실패(skip)가 있으면 Job이 COMPLETED여도 알림을 보낸다")
  void afterJob_whenItemsSkipped_notifiesWithoutDemotion() {
    // given
    JobExecution jobExecution = jobExecution();
    completedStep(jobExecution, "tmdbStep", 2); // 항목 2건 실패(skip)
    completedStep(jobExecution, "sportsDbStep", 0);

    // when
    listener.afterJob(jobExecution);

    // then - 부분 실패는 상태 강등 없이 알림만
    assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    then(discordNotifier).should().notify(captor.capture());
    assertThat(captor.getValue()).contains("failed=2");
  }

  private JobExecution jobExecution() {
    JobExecution jobExecution = MetaDataInstanceFactory.createJobExecution();
    jobExecution.setStatus(BatchStatus.COMPLETED);
    jobExecution.setExitStatus(ExitStatus.COMPLETED);
    return jobExecution;
  }

  private void completedStep(JobExecution jobExecution, String name, int writeSkipCount) {
    StepExecution stepExecution = jobExecution.createStepExecution(name);
    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setWriteSkipCount(writeSkipCount);
  }
}
