package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;

// 정규화된 수집 데이터를 건별 멱등 upsert로 저장하는 Writer
// - 예외를 잡지 않고 전파한다: upsert가 청크 tx에 참여하므로 writer 안에서 삼키면 rollback-only 오염으로
//   커밋 시점에 UnexpectedRollbackException이 난다. 항목 단위 격리는 스텝의 skip 설정이 담당
// - INSERTED/UPDATED/SKIPPED 집계는 청크 tx 커밋 후(afterChunk)에만 누계에 반영한다
//   (롤백된 청크의 임시 집계는 폐기. skip 스캔이 건별 tx로 재처리하며 다시 집계됨)
// - afterStep에서 누계를 ExecutionContext에 기록해 IngestionJobListener가 Job 요약에 사용한다
// - 실행마다 집계가 초기화되도록 @StepScope 빈으로 생성한다 (ContentIngestionJobConfig)
//
// ItemWriter - 실제 저장 로직
// ChunkListener - 청크 트랜잭션의 전후 실패 훅 받음
// StepExecutionListener - 스텝 종료 시 결과를 밖으로 내보냄
@Slf4j
public class ContentUpsertItemWriter
    implements ItemWriter<ExternalContentData>, StepExecutionListener, ChunkListener {

  public static final String CONTEXT_KEY_INSERTED = "inserted";
  public static final String CONTEXT_KEY_UPDATED = "updated";
  public static final String CONTEXT_KEY_SKIPPED = "skipped";

  private final ContentUpsertService contentUpsertService;
  private final ContentSource source;

  // 커밋된 청크 누계
  private int inserted;
  private int updated;
  private int skipped;
  // 현재 청크 트랜잭션 내 임시 집계
  private int chunkInserted;
  private int chunkUpdated;
  private int chunkSkipped;

  public ContentUpsertItemWriter(ContentUpsertService contentUpsertService, ContentSource source) {
    this.contentUpsertService = contentUpsertService;
    this.source = source;
  }

  // 청크의 각 항목을 upsert하고 반환된 결과에 따라 임시 카운터를 올림
  @Override
  public void write(Chunk<? extends ExternalContentData> chunk) {
    for (ExternalContentData item : chunk) {
      switch (contentUpsertService.upsert(item)) {
        case INSERTED -> chunkInserted++;
        case UPDATED -> chunkUpdated++;
        case SKIPPED -> chunkSkipped++;
      }
    }
  }

  // 임시 카운터는 청크 경계에서 항상 리셋되고, 오직 커밋된 청크만 누계에 반영된다.
  // 청크 시작 전 임시 카운터를 0으로 초기화
  @Override
  public void beforeChunk(ChunkContext context) {
    resetChunkCounts();
  }

  // 청크 트랜잭션이 커밋된 후 호출됨 - 임시 집계를 누계에 더함
  @Override
  public void afterChunk(ChunkContext context) {
    // 청크 tx 커밋 후 호출되므로 이 시점에만 누계에 반영
    inserted += chunkInserted;
    updated += chunkUpdated;
    skipped += chunkSkipped;
    resetChunkCounts(); // 다시 reset해 다음 청크를 준비
  }

  // 청크 실패(롤백)했을 때 호출됨 - 임시 카운터만 reset함
  @Override
  public void afterChunkError(ChunkContext context) {
    resetChunkCounts();
  }

  // 결과 내보내기 - step이 끝나면 확정된 누계를 step의 ExecutionContext에 작성
  @Override
  public ExitStatus afterStep(StepExecution stepExecution) {
    ExecutionContext context = stepExecution.getExecutionContext();
    context.putInt(CONTEXT_KEY_INSERTED, inserted);
    context.putInt(CONTEXT_KEY_UPDATED, updated);
    context.putInt(CONTEXT_KEY_SKIPPED, skipped);
    // 기존 소스별 수집 완료 로그와 동등한 요약 (failed = skip 건수)
    log.info(
        "콘텐츠 수집 완료. source={}, fetched={}, inserted={}, updated={}, skipped={}, failed={}",
        source,
        stepExecution.getReadCount(),
        inserted,
        updated,
        skipped,
        stepExecution.getSkipCount());
    return null; // ExitStatus 변경 없음
  }

  private void resetChunkCounts() {
    chunkInserted = 0;
    chunkUpdated = 0;
    chunkSkipped = 0;
  }
}
