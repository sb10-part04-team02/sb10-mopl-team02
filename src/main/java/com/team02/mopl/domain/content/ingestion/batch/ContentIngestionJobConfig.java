package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.sportsdb.SportsDbContentFetcher;
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

// 콘텐츠 수집 배치 Job 구성: contentIngestionJob = tmdbStep -> sportsDbStep (소스당 Tasklet 스텝 1개)
// - 소스 간 실패 격리: tmdbStep이 실패해도 on("*") 전이로 sportsDbStep은 실행된다
//   (이 flow가 스텝 실패를 COMPLETED로 가릴 수 있어 최종 상태는 IngestionJobListener가 판정)
// - 항목 단위 실패 격리 + 집계는 ContentIngestionTasklet이 담당 (chunk의 reader/writer/skip 리스너 불필요)
// - 트랜잭션: tasklet 스텝에 ResourcelessTransactionManager(no-op)를 준다. 실제 커밋 경계는 오직
//   ContentUpsertService.upsert(@Transactional)가 건별로 만든다. 만약 실제 tx로 스텝을 감싸면
//   upsert가 그 tx에 합류(REQUIRED)해 불량 1건이 tx를 rollback-only로 오염시키고, catch로 삼켜도
//   커밋 시 UnexpectedRollbackException이 난다. no-op 매니저가 이 오염을 원천 차단한다.
@Configuration
public class ContentIngestionJobConfig {

  public static final String JOB_NAME = "contentIngestionJob";

  // 항목 단위 실패는 격리하되, 시스템 장애(DB 다운 등)로 전부 실패하는 상황은 스텝 실패로 드러나도록 상한을 둔다
  private static final int SKIP_LIMIT = 100;

  // tasklet 본문을 실제 DB 트랜잭션으로 감싸지 않기 위한 no-op 트랜잭션 매니저 (건별 tx는 upsert가 담당)
  private final PlatformTransactionManager taskletTransactionManager =
      new ResourcelessTransactionManager();

  @Bean
  public Step tmdbStep(
      JobRepository jobRepository,
      TmdbContentFetcher tmdbContentFetcher,
      ContentUpsertService contentUpsertService) {
    return buildStep("tmdbStep", jobRepository, tmdbContentFetcher, contentUpsertService);
  }

  @Bean
  public Step sportsDbStep(
      JobRepository jobRepository,
      SportsDbContentFetcher sportsDbContentFetcher,
      ContentUpsertService contentUpsertService) {
    return buildStep("sportsDbStep", jobRepository, sportsDbContentFetcher, contentUpsertService);
  }

  @Bean
  public Job contentIngestionJob(
      JobRepository jobRepository,
      Step tmdbStep,
      Step sportsDbStep,
      IngestionJobListener ingestionJobListener) {
    return new JobBuilder(JOB_NAME, jobRepository)
        .listener(ingestionJobListener)
        .start(tmdbStep)
        .on("*")
        .to(sportsDbStep)
        .from(sportsDbStep)
        .on("*")
        .end()
        .end()
        .build();
  }

  // tmdbStep/sportsDbStep은 각자 자기 소스의 fetcher를 tasklet에 넘기는 게 전부 -> 공통 로직 추상화
  private Step buildStep(
      String name,
      JobRepository jobRepository,
      ContentFetcher fetcher,
      ContentUpsertService contentUpsertService) {
    return new StepBuilder(name, jobRepository)
        .tasklet(
            new ContentIngestionTasklet(fetcher, contentUpsertService, SKIP_LIMIT),
            taskletTransactionManager)
        .build();
  }
}
