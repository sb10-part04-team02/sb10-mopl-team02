package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.UpsertResult;
import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TmdbIngestionRunnerTest {

  @Mock private TmdbContentFetcher tmdbContentFetcher;

  @Mock private ContentUpsertService contentUpsertService;

  @InjectMocks private TmdbIngestionRunner runner;

  private static ExternalContentData data(String externalId) {
    return new ExternalContentData(
        ContentSource.TMDB, externalId, ContentType.MOVIE, "제목", "줄거리", "http://img", List.of());
  }

  @Test
  @DisplayName("수집된 콘텐츠를 건별로 upsert하고, 한 건이 실패해도 나머지를 계속 처리한다")
  void run_upsertsEachItemAndContinuesAfterFailure() {
    ExternalContentData first = data("1");
    ExternalContentData second = data("2");
    ExternalContentData third = data("3");
    given(tmdbContentFetcher.fetch()).willReturn(List.of(first, second, third));
    given(contentUpsertService.upsert(first)).willReturn(UpsertResult.INSERTED);
    given(contentUpsertService.upsert(second)).willThrow(new RuntimeException("저장 실패"));
    given(contentUpsertService.upsert(third)).willReturn(UpsertResult.UPDATED);

    runner.run(null);

    then(contentUpsertService).should().upsert(first);
    then(contentUpsertService).should().upsert(second);
    then(contentUpsertService).should().upsert(third);
  }

  @Test
  @DisplayName("수집 자체가 실패해도 예외를 전파하지 않는다 (애플리케이션 기동 보호)")
  void run_whenFetchFails_doesNotPropagate() {
    given(tmdbContentFetcher.fetch())
        .willThrow(new TmdbApiException(new RuntimeException("인증 실패")));

    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
    then(contentUpsertService).should(never()).upsert(any());
  }
}
