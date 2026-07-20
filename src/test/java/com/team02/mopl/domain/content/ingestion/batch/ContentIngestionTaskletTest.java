package com.team02.mopl.domain.content.ingestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.UpsertResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.test.MetaDataInstanceFactory;

@ExtendWith(MockitoExtension.class)
class ContentIngestionTaskletTest {

  @Mock private ContentFetcher fetcher;
  @Mock private ContentUpsertService contentUpsertService;

  private static ExternalContentData tmdbData(String externalId) {
    return new ExternalContentData(
        ContentSource.TMDB, externalId, ContentType.MOVIE, "제목", "줄거리", "http://img", List.of());
  }

  @Test
  @DisplayName("대상이 1건 이상인데 전부 실패하면 skipLimit 미달이어도 스텝을 실패시키고, 그 시점 집계를 ExecutionContext에 남긴다")
  void execute_whenAllItemsFail_promotesToStepFailureAndPreservesCounts() {
    // given - 3건을 fetch하지만 upsert가 전부 실패, skipLimit은 넉넉히 100 (초과 조건은 걸리지 않음)
    given(fetcher.source()).willReturn(ContentSource.TMDB);
    given(fetcher.fetch()).willReturn(List.of(tmdbData("1"), tmdbData("2"), tmdbData("3")));
    given(contentUpsertService.upsert(any())).willThrow(new RuntimeException("저장 실패"));
    ContentIngestionTasklet tasklet =
        new ContentIngestionTasklet(fetcher, contentUpsertService, 100, IngestionMode.DAILY);

    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    StepContribution contribution = stepExecution.createStepContribution();
    ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

    // when & then - 전부 실패(1건도 성공 못 함)는 skipLimit 초과가 아니어도 스텝 실패로 승격된다
    assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext))
        .isInstanceOf(ContentUpsertFailureException.class)
        .hasMessageContaining("전부가 실패");

    // 승격 실패로 던지더라도 그 시점까지의 집계는 ExecutionContext에 보존된다 (전부 실패이므로 0)
    ExecutionContext context = stepExecution.getExecutionContext();
    assertThat(context.getInt(ContentIngestionTasklet.CONTEXT_KEY_INSERTED)).isZero();
    assertThat(context.getInt(ContentIngestionTasklet.CONTEXT_KEY_UPDATED)).isZero();
    assertThat(context.getInt(ContentIngestionTasklet.CONTEXT_KEY_SKIPPED)).isZero();
  }

  @Test
  @DisplayName("실패 건수가 skipLimit을 초과하면 스텝을 실패시키고, 그 시점까지의 성공 집계는 보존한다")
  void execute_whenFailedExceedsSkipLimit_promotesToStepFailure() {
    // given - 4건 중 1건만 성공하고 나머지는 실패, skipLimit은 1 (2번째 실패에서 초과)
    given(fetcher.source()).willReturn(ContentSource.TMDB);
    given(fetcher.fetch())
        .willReturn(List.of(tmdbData("1"), tmdbData("2"), tmdbData("3"), tmdbData("4")));
    given(contentUpsertService.upsert(any()))
        .willReturn(UpsertResult.INSERTED)
        .willThrow(new RuntimeException("저장 실패"));
    ContentIngestionTasklet tasklet =
        new ContentIngestionTasklet(fetcher, contentUpsertService, 1, IngestionMode.DAILY);

    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    StepContribution contribution = stepExecution.createStepContribution();
    ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

    // when & then - 전부 실패가 아니어도 skipLimit 초과만으로 스텝 실패로 승격된다
    assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext))
        .isInstanceOf(ContentUpsertFailureException.class)
        .hasMessageContaining("skipLimit(1)을 초과");

    // 승격 실패로 던져도 이미 커밋된 성공 건수는 유실되지 않는다
    ExecutionContext context = stepExecution.getExecutionContext();
    assertThat(context.getInt(ContentIngestionTasklet.CONTEXT_KEY_INSERTED)).isEqualTo(1);
  }

  @Test
  @DisplayName("fetch가 실패하면 ContentFetchException으로 감싸 스텝을 즉시 실패시킨다")
  void execute_whenFetchFails_wrapsInContentFetchException() {
    // given - 소스 수준 장애 (항목 단위 실패와 구분되어야 함)
    RuntimeException cause = new RuntimeException("TMDB 장애");
    given(fetcher.source()).willReturn(ContentSource.TMDB);
    given(fetcher.fetch()).willThrow(cause);
    ContentIngestionTasklet tasklet =
        new ContentIngestionTasklet(fetcher, contentUpsertService, 100, IngestionMode.DAILY);

    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    StepContribution contribution = stepExecution.createStepContribution();
    ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

    // when & then
    assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext))
        .isInstanceOf(ContentFetchException.class)
        .hasMessageContaining("source=TMDB")
        .hasCause(cause);
  }
}
