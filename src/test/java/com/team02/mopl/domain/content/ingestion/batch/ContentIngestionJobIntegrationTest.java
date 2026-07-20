package com.team02.mopl.domain.content.ingestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.sportsdb.SportsDbContentFetcher;
import com.team02.mopl.domain.content.ingestion.tmdb.TmdbContentFetcher;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.global.alert.DiscordNotifier;
import com.team02.mopl.support.IntegrationTestSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 콘텐츠 수집 배치 Job 통합 테스트
class ContentIngestionJobIntegrationTest extends IntegrationTestSupport {

  @Autowired private JobLauncher jobLauncher;
  @Autowired private Job contentIngestionJob;
  @Autowired private ContentRepository contentRepository;
  @Autowired private TagRepository tagRepository;

  @MockitoBean private TmdbContentFetcher tmdbContentFetcher;
  @MockitoBean private SportsDbContentFetcher sportsDbContentFetcher;
  @MockitoBean private DiscordNotifier discordNotifier;
  @MockitoSpyBean private ContentUpsertService contentUpsertService;

  @Test
  @DisplayName("정상 실행 시 Job이 COMPLETED되고 콘텐츠와 태그가 저장되며 알림은 없다")
  void job_happyPath_persistsContentsWithoutAlert() throws Exception {
    // given
    String run = uniqueSuffix();
    given(tmdbContentFetcher.fetch())
        .willReturn(List.of(tmdbMovie(run + "-1"), tmdbMovie(run + "-2")));
    given(sportsDbContentFetcher.fetch()).willReturn(List.of(sportsEvent(run + "-1")));

    // when
    JobExecution execution = launchJob();

    // then
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    StepExecution tmdbStep = stepByName(execution, "popularTmdbStep");
    assertThat(tmdbStep.getWriteCount()).isEqualTo(2);
    assertThat(tmdbStep.getExecutionContext().getInt(ContentIngestionTasklet.CONTEXT_KEY_INSERTED))
        .isEqualTo(2);

    Optional<Content> saved =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "movie:" + run + "-1");
    assertThat(saved).isPresent();
    assertThat(tagRepository.findByContentIdAndDeletedAtIsNull(saved.get().getId()))
        .extracting(Tag::getName)
        .containsExactly("액션");
    assertThat(
            contentRepository.findBySourceAndExternalId(
                ContentSource.SPORTS_DB, "event:" + run + "-1"))
        .isPresent();
    then(discordNotifier).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("멱등성: 같은 데이터로 재실행해도 중복 생성 없이 UPDATED로 집계된다")
  void job_rerunWithSameData_isIdempotent() throws Exception {
    // given
    String run = uniqueSuffix();
    List<ExternalContentData> tmdbData = List.of(tmdbMovie(run + "-1"), tmdbMovie(run + "-2"));
    given(tmdbContentFetcher.fetch()).willReturn(tmdbData);
    given(sportsDbContentFetcher.fetch()).willReturn(List.of(sportsEvent(run + "-1")));

    // when - 서로 다른 파라미터(새 JobInstance)로 2회 실행
    launchJob();
    JobExecution second = launchJob();

    // then - 2회차는 전부 UPDATED, 신규 생성 없음
    assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    StepExecution tmdbStep = stepByName(second, "popularTmdbStep");
    assertThat(tmdbStep.getExecutionContext().getInt(ContentIngestionTasklet.CONTEXT_KEY_INSERTED))
        .isZero();
    assertThat(tmdbStep.getExecutionContext().getInt(ContentIngestionTasklet.CONTEXT_KEY_UPDATED))
        .isEqualTo(2);
    // 중복 행이 있으면 단건 조회가 IncorrectResultSizeDataAccessException으로 실패한다
    assertThat(
            contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "movie:" + run + "-1"))
        .isPresent();
    assertThat(
            contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "movie:" + run + "-2"))
        .isPresent();
  }

  @Test
  @DisplayName("불량 항목 1건은 skip으로 격리되어 나머지는 저장되고 부분 실패 알림이 발송된다")
  void job_poisonItem_isSkippedAndOthersPersisted() throws Exception {
    // given
    String run = uniqueSuffix();
    ExternalContentData poison = tmdbMovie(run + "-poison");
    given(tmdbContentFetcher.fetch())
        .willReturn(List.of(tmdbMovie(run + "-1"), poison, tmdbMovie(run + "-2")));
    given(sportsDbContentFetcher.fetch()).willReturn(List.of());
    willThrow(new RuntimeException("불량 항목"))
        .given(contentUpsertService)
        .upsert(argThat(data -> poison.externalId().equals(data.externalId())));

    // when
    JobExecution execution = launchJob();

    // then - 항목 단위 실패는 Job을 실패시키지 않고 해당 건만 건너뛴다
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    StepExecution tmdbStep = stepByName(execution, "popularTmdbStep");
    assertThat(tmdbStep.getSkipCount()).isEqualTo(1);
    assertThat(tmdbStep.getExecutionContext().getInt(ContentIngestionTasklet.CONTEXT_KEY_INSERTED))
        .isEqualTo(2);
    assertThat(contentRepository.findBySourceAndExternalId(ContentSource.TMDB, poison.externalId()))
        .isEmpty();
    assertThat(
            contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "movie:" + run + "-1"))
        .isPresent();
    then(discordNotifier).should().notify(contains("failed=1"));
  }

  @Test
  @DisplayName("한 소스의 fetch 전체 실패 시 다른 소스는 수집되고 Job은 FAILED로 알림이 발송된다")
  void job_whenOneSourceFails_otherSourceStillRunsAndAlerts() throws Exception {
    // given
    String run = uniqueSuffix();
    given(tmdbContentFetcher.fetch()).willThrow(new RuntimeException("TMDB 장애"));
    given(tmdbContentFetcher.source()).willReturn(ContentSource.TMDB);
    given(sportsDbContentFetcher.fetch()).willReturn(List.of(sportsEvent(run + "-1")));

    // when
    JobExecution execution = launchJob();

    // then - 소스 간 실패 격리 + 부분 실패가 COMPLETED로 가려지지 않음
    assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
    // 실패한 스텝을 지나 flow가 진행되면 스텝 상태는 ABANDONED로 남고 실패는 ExitStatus에 남는다
    assertThat(stepByName(execution, "popularTmdbStep").getExitStatus().getExitCode())
        .isEqualTo(ExitStatus.FAILED.getExitCode());
    assertThat(stepByName(execution, "sportsDbStep").getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            contentRepository.findBySourceAndExternalId(
                ContentSource.SPORTS_DB, "event:" + run + "-1"))
        .isPresent();
    then(discordNotifier).should().notify(contains("상태=FAILED"));
  }

  // 매 호출이 새 JobInstance가 되도록 실제 트리거와 동일한 timestamp 파라미터에 유일 값 uuid를 더해 실행
  private JobExecution launchJob() throws Exception {
    JobParameters parameters =
        new JobParametersBuilder()
            .addLocalDateTime("runDateTime", LocalDateTime.now())
            .addString("uuid", UUID.randomUUID().toString())
            .toJobParameters();
    return jobLauncher.run(contentIngestionJob, parameters);
  }

  private StepExecution stepByName(JobExecution execution, String name) {
    return execution.getStepExecutions().stream()
        .filter(step -> step.getStepName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  private String uniqueSuffix() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private ExternalContentData tmdbMovie(String suffix) {
    return new ExternalContentData(
        ContentSource.TMDB,
        "movie:" + suffix,
        ContentType.MOVIE,
        "영화 " + suffix,
        "설명",
        "https://img.example/" + suffix,
        List.of("액션"));
  }

  private ExternalContentData sportsEvent(String suffix) {
    return new ExternalContentData(
        ContentSource.SPORTS_DB,
        "event:" + suffix,
        ContentType.SPORT,
        "경기 " + suffix,
        "설명",
        "https://img.example/" + suffix,
        List.of("축구"));
  }
}
