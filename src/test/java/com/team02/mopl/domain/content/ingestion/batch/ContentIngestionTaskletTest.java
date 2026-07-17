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
        new ContentIngestionTasklet(fetcher, contentUpsertService, 100);

    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    StepContribution contribution = stepExecution.createStepContribution();
    ChunkContext chunkContext = new ChunkContext(new StepContext(stepExecution));

    // when & then - 전부 실패(1건도 성공 못 함)는 skipLimit 초과가 아니어도 스텝 실패로 승격된다
    assertThatThrownBy(() -> tasklet.execute(contribution, chunkContext))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("전부가 실패");

    // 승격 실패로 던지더라도 그 시점까지의 집계는 ExecutionContext에 보존된다 (전부 실패이므로 0)
    ExecutionContext context = stepExecution.getExecutionContext();
    assertThat(context.getInt(ContentIngestionTasklet.CONTEXT_KEY_INSERTED)).isZero();
    assertThat(context.getInt(ContentIngestionTasklet.CONTEXT_KEY_UPDATED)).isZero();
    assertThat(context.getInt(ContentIngestionTasklet.CONTEXT_KEY_SKIPPED)).isZero();
  }
}
