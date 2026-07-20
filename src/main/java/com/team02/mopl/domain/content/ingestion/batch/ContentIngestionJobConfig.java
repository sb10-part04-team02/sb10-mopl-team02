package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.sportsdb.SportsDbContentFetcher;
import com.team02.mopl.domain.content.ingestion.tmdb.TmdbBackfillContentFetcher;
import com.team02.mopl.domain.content.ingestion.tmdb.TmdbContentFetcher;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

// 콘텐츠 수집 배치 Job 구성:
//   contentIngestionJob = popularTmdbStep -> sportsDbStep -> backfillTmdbStep (스텝당 Tasklet 1개)
// - 모드 게이팅: 스텝마다 IngestionMode를 부여하고, JOB_PARAM_MODE와 일치하는 스텝만 실제 수집한다.
//   DAILY 실행은 popular+sports만, HOURLY 실행은 discover 백필만 돈다 (나머지는 fetch 없이 FINISHED).
// - 소스/모드 간 실패 격리: 한 스텝이 실패해도 on("*") 전이로 다음 스텝은 실행된다
//   (이 flow가 스텝 실패를 COMPLETED로 가릴 수 있어 최종 상태는 IngestionJobListener가 판정)
// - 항목 단위 실패 격리 + 집계는 ContentIngestionTasklet이 담당 (chunk의 reader/writer/skip 리스너 불필요)
// - 트랜잭션: tasklet 스텝에 ResourcelessTransactionManager(no-op)를 준다. 실제 커밋 경계는 오직
//   ContentUpsertService.upsert(@Transactional)가 건별로 만든다. 만약 실제 tx로 스텝을 감싸면
//   upsert가 그 tx에 합류(REQUIRED)해 불량 1건이 tx를 rollback-only로 오염시키고, catch로 삼켜도
//   커밋 시 UnexpectedRollbackException이 난다. no-op 매니저가 이 오염을 원천 차단한다.
@Configuration
public class ContentIngestionJobConfig {

  public static final String JOB_NAME = "contentIngestionJob";

  // 실행 모드를 지정하는 Job 파라미터 (IngestionMode 이름). 없으면 DAILY로 간주 (ContentIngestionTasklet 참고).
  public static final String JOB_PARAM_MODE = "mode";

  // 항목 단위 실패는 격리하되, 시스템 장애(DB 다운 등)로 전부 실패하는 상황은 스텝 실패로 드러나도록 상한을 둔다
  private static final int SKIP_LIMIT = 100;

  // tasklet 본문을 실제 DB 트랜잭션으로 감싸지 않기 위한 no-op 트랜잭션 매니저 (건별 tx는 upsert가 담당)
  private final PlatformTransactionManager taskletTransactionManager =
      new ResourcelessTransactionManager();

  @Bean
  public Step popularTmdbStep(
      JobRepository jobRepository,
      TmdbContentFetcher tmdbContentFetcher,
      ContentUpsertService contentUpsertService) {
    return buildStep(
        "popularTmdbStep",
        jobRepository,
        tmdbContentFetcher,
        contentUpsertService,
        IngestionMode.DAILY);
  }

  @Bean
  public Step sportsDbStep(
      JobRepository jobRepository,
      SportsDbContentFetcher sportsDbContentFetcher,
      ContentUpsertService contentUpsertService) {
    return buildStep(
        "sportsDbStep",
        jobRepository,
        sportsDbContentFetcher,
        contentUpsertService,
        IngestionMode.DAILY);
  }

  @Bean
  public Step backfillTmdbStep(
      JobRepository jobRepository,
      TmdbBackfillContentFetcher tmdbBackfillContentFetcher,
      ContentUpsertService contentUpsertService) {
    return buildStep(
        "backfillTmdbStep",
        jobRepository,
        tmdbBackfillContentFetcher,
        contentUpsertService,
        IngestionMode.HOURLY);
  }

  @Bean
  public Job contentIngestionJob(
      JobRepository jobRepository,
      Step popularTmdbStep,
      Step sportsDbStep,
      Step backfillTmdbStep,
      IngestionJobListener ingestionJobListener) {
    return new JobBuilder(JOB_NAME, jobRepository)
        .listener(ingestionJobListener)
        .start(popularTmdbStep)
        .on("*")
        .to(sportsDbStep)
        .from(sportsDbStep)
        .on("*")
        .to(backfillTmdbStep)
        .from(backfillTmdbStep)
        .on("*")
        .end()
        .end()
        .build();
  }

  // 각 스텝은 자기 소스의 fetcher와 모드를 tasklet에 넘기는 게 전부 -> 공통 로직 추상화
  private Step buildStep(
      String name,
      JobRepository jobRepository,
      ContentFetcher fetcher,
      ContentUpsertService contentUpsertService,
      IngestionMode stepMode) {
    return new StepBuilder(name, jobRepository)
        .tasklet(
            new ContentIngestionTasklet(fetcher, contentUpsertService, SKIP_LIMIT, stepMode),
            taskletTransactionManager)
        .build();
  }
}
