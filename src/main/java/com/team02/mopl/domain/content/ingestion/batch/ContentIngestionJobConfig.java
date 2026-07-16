package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.sportsdb.SportsDbContentFetcher;
import com.team02.mopl.domain.content.ingestion.tmdb.TmdbContentFetcher;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

// 콘텐츠 수집 배치 Job 구성: contentIngestionJob = tmdbStep -> sportsDbStep
// - 소스 간 실패 격리: tmdbStep이 실패해도 on("*") 전이로 sportsDbStep은 실행된다
//   (이 flow가 스텝 실패를 COMPLETED로 가릴 수 있어 최종 상태는 IngestionJobListener가 판정)
// - 항목 단위 실패 격리: faultTolerant skip. 청크가 롤백된 뒤 건별 재처리(스캔)로 불량 항목만 건너뛴다
//   (기존 건별 @Transactional + catch 집계와 동등한 동작)
// - reader/writer는 실행마다 상태(순회 위치/집계)가 초기화되도록 @StepScope
@Configuration
public class ContentIngestionJobConfig {

  public static final String JOB_NAME = "contentIngestionJob";

  private static final int CHUNK_SIZE = 50;
  // 항목 단위 실패는 skip으로 격리하되, 시스템 장애(DB 다운 등)로 전부 실패하는 상황은 스텝 실패로 드러나도록 상한을 둔다
  private static final int SKIP_LIMIT = 100;

  @Bean
  @StepScope
  public ContentFetcherItemReader tmdbItemReader(TmdbContentFetcher fetcher) {
    return new ContentFetcherItemReader(fetcher);
  }

  @Bean
  @StepScope
  public ContentFetcherItemReader sportsDbItemReader(SportsDbContentFetcher fetcher) {
    return new ContentFetcherItemReader(fetcher);
  }

  @Bean
  @StepScope
  public ContentUpsertItemWriter tmdbItemWriter(ContentUpsertService contentUpsertService) {
    return new ContentUpsertItemWriter(contentUpsertService, ContentSource.TMDB);
  }

  @Bean
  @StepScope
  public ContentUpsertItemWriter sportsDbItemWriter(ContentUpsertService contentUpsertService) {
    return new ContentUpsertItemWriter(contentUpsertService, ContentSource.SPORTS_DB);
  }

  @Bean
  public Step tmdbStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ContentFetcherItemReader tmdbItemReader,
      ContentUpsertItemWriter tmdbItemWriter,
      IngestionSkipListener skipListener) {
    return buildStep(
        "tmdbStep",
        jobRepository,
        transactionManager,
        tmdbItemReader,
        tmdbItemWriter,
        skipListener);
  }

  @Bean
  public Step sportsDbStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ContentFetcherItemReader sportsDbItemReader,
      ContentUpsertItemWriter sportsDbItemWriter,
      IngestionSkipListener skipListener) {
    return buildStep(
        "sportsDbStep",
        jobRepository,
        transactionManager,
        sportsDbItemReader,
        sportsDbItemWriter,
        skipListener);
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

  // tmdbStep과 sportsDbStep은 각자 자기 소스의 Reader·Writer를 받아 buildStep에 넘기는 게 전부 -> 공통 로직 추상화
  private Step buildStep(
      String name,
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      ContentFetcherItemReader reader,
      ContentUpsertItemWriter writer,
      IngestionSkipListener skipListener) {
    return new StepBuilder(name, jobRepository)
        .<ExternalContentData, ExternalContentData>chunk(CHUNK_SIZE, transactionManager)
        .reader(reader)
        .writer(writer)
        // 항목 단위 실패 격리
        .faultTolerant() // Step 실행 중 오류가 발생했을 때 특정 오류를 건너뛰거나 다시 시도할 수 있는 기능 on
        .skip(Exception.class) // 어떤 예외든
        .noSkip(ContentFetchException.class) // fetch 전체 실패는 skip이 아니라 스텝 실패로
        .skipLimit(SKIP_LIMIT)
        // 리스너
        .listener(skipListener)
        .listener((StepExecutionListener) writer) // afterStep에서 집계를 ExecutionContext에 기록
        .listener((ChunkListener) writer) // 청크 커밋/롤백 시점에 집계 반영/폐기
        .build();
  }
}
