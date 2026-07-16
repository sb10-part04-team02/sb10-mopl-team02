package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

// 소스 1개(TMDB/SportsDB ...)의 수집을 담당하는 Tasklet
// - fetch -> 건별 멱등 upsert 흐름을 하나의 execute()에서 순회한다 (chunk reader/processor/writer 불필요)
// - fetch 전체 실패는 ContentFetchException으로 전파해 스텝을 즉시 실패시킨다 (소스 수준 장애)
// - 개별 항목 실패는 catch로 격리해 failed로 집계하고, skipLimit을 초과하면(전부 실패=DB 다운 등)
//   스텝 실패로 승격한다 (기존 ContentCollectService의 건별 catch 집계 + 시스템 장애 구분과 동등)
// - 각 upsert는 자기 트랜잭션에서 커밋된다: 이 스텝은 ResourcelessTransactionManager로 구성되어
//   tasklet 본문을 DB 트랜잭션으로 감싸지 않으므로, 불량 1건의 롤백이 다른 항목을 오염시키지 않는다
//   (ContentIngestionJobConfig 참고)
// - 집계(inserted/updated/skipped)는 스텝 ExecutionContext에 기록해 IngestionJobListener가 Job 요약에 사용한다
@Slf4j
public class ContentIngestionTasklet implements Tasklet {

  public static final String CONTEXT_KEY_INSERTED = "inserted";
  public static final String CONTEXT_KEY_UPDATED = "updated";
  public static final String CONTEXT_KEY_SKIPPED = "skipped";

  private final ContentFetcher fetcher;
  private final ContentUpsertService contentUpsertService;
  private final int skipLimit;

  public ContentIngestionTasklet(
      ContentFetcher fetcher, ContentUpsertService contentUpsertService, int skipLimit) {
    this.fetcher = fetcher;
    this.contentUpsertService = contentUpsertService;
    this.skipLimit = skipLimit;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    List<ExternalContentData> contents;
    try {
      contents = fetcher.fetch(); // 페이지/리그 단위 실패는 fetcher가 이미 격리
    } catch (Exception e) {
      throw new ContentFetchException(fetcher.source(), e); // fetch 전체 실패 -> 스텝 실패
    }

    int inserted = 0;
    int updated = 0;
    int skipped = 0;
    int failed = 0;
    for (ExternalContentData data : contents) {
      contribution.incrementReadCount(); // StepExecution.readCount 유지
      try {
        switch (contentUpsertService.upsert(data)) { // @Transactional - 건별 자체 tx로 커밋/롤백
          case INSERTED -> inserted++;
          case UPDATED -> updated++;
          case SKIPPED -> skipped++;
        }
        contribution.incrementWriteCount(1); // 예외 없이 처리된 건 = 저장 성공 (StepExecution.writeCount)
      } catch (Exception e) {
        failed++;
        contribution.incrementWriteSkipCount(); // StepExecution.skipCount 유지 (요약의 failed)
        log.warn(
            "콘텐츠 upsert 실패로 건너뜁니다. source={}, externalId={}", data.source(), data.externalId(), e);
        if (failed > skipLimit) {
          // 항목 단위 실패는 skip으로 격리하되, 전부 실패(DB 다운 등)는 스텝 실패로 드러낸다
          throw new IllegalStateException(
              "수집 실패 건수가 skipLimit(" + skipLimit + ")을 초과했습니다. source=" + fetcher.source(), e);
        }
      }
    }

    // 확정된 집계를 스텝 ExecutionContext에 기록 (IngestionJobListener가 Job 요약/알림에 사용)
    ExecutionContext context =
        chunkContext.getStepContext().getStepExecution().getExecutionContext();
    context.putInt(CONTEXT_KEY_INSERTED, inserted);
    context.putInt(CONTEXT_KEY_UPDATED, updated);
    context.putInt(CONTEXT_KEY_SKIPPED, skipped);

    log.info(
        "콘텐츠 수집 완료. source={}, fetched={}, inserted={}, updated={}, skipped={}, failed={}",
        fetcher.source(),
        contents.size(),
        inserted,
        updated,
        skipped,
        failed);
    return RepeatStatus.FINISHED;
  }
}
