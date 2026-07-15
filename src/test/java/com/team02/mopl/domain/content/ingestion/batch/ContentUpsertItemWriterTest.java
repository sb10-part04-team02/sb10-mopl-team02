package com.team02.mopl.domain.content.ingestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.UpsertResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.test.MetaDataInstanceFactory;

@ExtendWith(MockitoExtension.class)
class ContentUpsertItemWriterTest {

  @Mock private ContentUpsertService contentUpsertService;

  private ContentUpsertItemWriter writer;

  @BeforeEach
  void setUp() {
    writer = new ContentUpsertItemWriter(contentUpsertService, ContentSource.TMDB);
  }

  @Test
  @DisplayName("upsert 결과별 집계는 청크 커밋(afterChunk) 후 누계에 반영되고 afterStep이 ExecutionContext에 기록한다")
  void write_talliesPerResult_andAfterStepStoresCounts() {
    // given
    given(contentUpsertService.upsert(any()))
        .willReturn(UpsertResult.INSERTED, UpsertResult.UPDATED, UpsertResult.SKIPPED);

    // when - 청크 처리(쓰기 -> 커밋) 후 스텝 종료
    writer.write(new Chunk<>(List.of(data("movie:1"), data("movie:2"), data("movie:3"))));
    writer.afterChunk(null);
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    writer.afterStep(stepExecution);

    // then
    assertThat(
            stepExecution
                .getExecutionContext()
                .getInt(ContentUpsertItemWriter.CONTEXT_KEY_INSERTED))
        .isEqualTo(1);
    assertThat(
            stepExecution.getExecutionContext().getInt(ContentUpsertItemWriter.CONTEXT_KEY_UPDATED))
        .isEqualTo(1);
    assertThat(
            stepExecution.getExecutionContext().getInt(ContentUpsertItemWriter.CONTEXT_KEY_SKIPPED))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("upsert 예외는 잡지 않고 전파한다 (항목 격리는 스텝의 skip 설정이 담당)")
  void write_whenUpsertFails_propagates() {
    // given
    given(contentUpsertService.upsert(any())).willThrow(new RuntimeException("upsert 실패"));

    // when & then
    assertThatThrownBy(() -> writer.write(new Chunk<>(List.of(data("movie:1")))))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("청크가 롤백(afterChunkError)되면 그 청크의 임시 집계는 폐기되어 재처리 시 중복 집계되지 않는다")
  void afterChunkError_discardsChunkCounts() {
    // given - 같은 항목이 실패 청크에서 한 번, skip 스캔 재처리에서 한 번 upsert되는 상황
    given(contentUpsertService.upsert(any())).willReturn(UpsertResult.INSERTED);

    // when - 1차 쓰기 후 롤백, 재처리 쓰기 후 커밋
    writer.write(new Chunk<>(List.of(data("movie:1"))));
    writer.afterChunkError(null);
    writer.write(new Chunk<>(List.of(data("movie:1"))));
    writer.afterChunk(null);
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    writer.afterStep(stepExecution);

    // then - 커밋된 1건만 집계되어야 한다
    assertThat(
            stepExecution
                .getExecutionContext()
                .getInt(ContentUpsertItemWriter.CONTEXT_KEY_INSERTED))
        .isEqualTo(1);
  }

  private ExternalContentData data(String externalId) {
    return new ExternalContentData(
        ContentSource.TMDB, externalId, ContentType.MOVIE, "제목", "설명", "https://img", List.of());
  }
}
