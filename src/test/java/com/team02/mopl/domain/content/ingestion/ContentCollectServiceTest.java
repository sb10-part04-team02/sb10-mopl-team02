package com.team02.mopl.domain.content.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.exception.SportsDbApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentCollectServiceTest {

  @Mock private ContentFetcher tmdbFetcher;

  @Mock private ContentFetcher sportsDbFetcher;

  @Mock private ContentUpsertService contentUpsertService;

  private ContentCollectService contentCollectService;

  @BeforeEach
  void setUp() {
    contentCollectService =
        new ContentCollectService(List.of(tmdbFetcher, sportsDbFetcher), contentUpsertService);
  }

  private static ExternalContentData tmdbData(String externalId) {
    return new ExternalContentData(
        ContentSource.TMDB, externalId, ContentType.MOVIE, "제목", "줄거리", "http://img", List.of());
  }

  private static ExternalContentData sportsDbData(String externalId) {
    return new ExternalContentData(
        ContentSource.SPORTS_DB,
        externalId,
        ContentType.SPORT,
        "경기",
        "설명",
        "http://img",
        List.of());
  }

  @Test
  @DisplayName("collectAll은 모든 소스를 수집해 소스별 결과를 집계한다")
  void collectAll_collectsAllSourcesAndAggregatesResults() {
    // given
    given(tmdbFetcher.source()).willReturn(ContentSource.TMDB);
    given(sportsDbFetcher.source()).willReturn(ContentSource.SPORTS_DB);
    ExternalContentData movie = tmdbData("movie:1");
    ExternalContentData sport = sportsDbData("event:1");
    given(tmdbFetcher.fetch()).willReturn(List.of(movie));
    given(sportsDbFetcher.fetch()).willReturn(List.of(sport));
    given(contentUpsertService.upsert(movie)).willReturn(UpsertResult.INSERTED);
    given(contentUpsertService.upsert(sport)).willReturn(UpsertResult.UPDATED);

    // when
    List<CollectResult> results = contentCollectService.collectAll();

    // then
    assertThat(results).hasSize(2);
    assertThat(results.get(0)).isEqualTo(new CollectResult(ContentSource.TMDB, 1, 1, 0, 0, 0));
    assertThat(results.get(1)).isEqualTo(new CollectResult(ContentSource.SPORTS_DB, 1, 0, 1, 0, 0));
  }

  @Test
  @DisplayName("한 건의 upsert가 실패해도 나머지를 계속 처리하고 failed로 집계한다 (항목 간 격리)")
  void collectAll_whenItemFails_continuesAndCountsFailed() {
    // given
    given(tmdbFetcher.source()).willReturn(ContentSource.TMDB);
    given(sportsDbFetcher.source()).willReturn(ContentSource.SPORTS_DB);
    ExternalContentData first = tmdbData("1");
    ExternalContentData second = tmdbData("2");
    ExternalContentData third = tmdbData("3");
    given(tmdbFetcher.fetch()).willReturn(List.of(first, second, third));
    given(sportsDbFetcher.fetch()).willReturn(List.of());
    given(contentUpsertService.upsert(first)).willReturn(UpsertResult.INSERTED);
    given(contentUpsertService.upsert(second)).willThrow(new RuntimeException("저장 실패"));
    given(contentUpsertService.upsert(third)).willReturn(UpsertResult.SKIPPED);

    // when
    List<CollectResult> results = contentCollectService.collectAll();

    // then
    then(contentUpsertService).should().upsert(third); // 실패 이후 항목도 처리됨
    assertThat(results.get(0)).isEqualTo(new CollectResult(ContentSource.TMDB, 3, 1, 0, 1, 1));
  }

  @Test
  @DisplayName("한 소스의 fetch가 실패해도 다음 소스는 계속 수집하고, 결과에는 성공한 소스만 담는다 (소스 간 격리)")
  void collectAll_whenSourceFails_continuesWithNextSource() {
    // given - TMDB는 수집 실패, SportsDB는 정상
    given(tmdbFetcher.source()).willReturn(ContentSource.TMDB);
    given(sportsDbFetcher.source()).willReturn(ContentSource.SPORTS_DB);
    given(tmdbFetcher.fetch()).willThrow(new RuntimeException("인증 실패"));
    ExternalContentData sport = sportsDbData("event:1");
    given(sportsDbFetcher.fetch()).willReturn(List.of(sport));
    given(contentUpsertService.upsert(sport)).willReturn(UpsertResult.INSERTED);

    // when
    List<CollectResult> results = contentCollectService.collectAll();

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).source()).isEqualTo(ContentSource.SPORTS_DB);
  }

  @Test
  @DisplayName("collect(source)는 지정한 소스만 수집한다")
  void collect_withSource_collectsOnlyThatSource() {
    // given
    given(tmdbFetcher.source()).willReturn(ContentSource.TMDB);
    given(sportsDbFetcher.source()).willReturn(ContentSource.SPORTS_DB);
    ExternalContentData sport = sportsDbData("event:1");
    given(sportsDbFetcher.fetch()).willReturn(List.of(sport));
    given(contentUpsertService.upsert(sport)).willReturn(UpsertResult.INSERTED);

    // when
    CollectResult result = contentCollectService.collect(ContentSource.SPORTS_DB);

    // then
    assertThat(result).isEqualTo(new CollectResult(ContentSource.SPORTS_DB, 1, 1, 0, 0, 0));
    then(tmdbFetcher).should(never()).fetch();
  }

  @Test
  @DisplayName("collect(source)는 등록되지 않은 소스면 IllegalArgumentException을 던진다")
  void collect_withUnregisteredSource_throwsIllegalArgumentException() {
    // given - SPORTS_DB fetcher가 등록되지 않은 서비스
    given(tmdbFetcher.source()).willReturn(ContentSource.TMDB);
    ContentCollectService onlyTmdb =
        new ContentCollectService(List.of(tmdbFetcher), contentUpsertService);

    // when & then
    assertThatThrownBy(() -> onlyTmdb.collect(ContentSource.SPORTS_DB))
        .isInstanceOf(IllegalArgumentException.class);
    then(contentUpsertService).should(never()).upsert(any());
  }

  @Test
  @DisplayName("소스 수집기 자체 예외(ExternalApiException)도 소스 간 격리 대상이다")
  void collectAll_whenExternalApiExceptionThrown_isolatesSource() {
    // given
    given(tmdbFetcher.source()).willReturn(ContentSource.TMDB);
    given(sportsDbFetcher.source()).willReturn(ContentSource.SPORTS_DB);
    given(tmdbFetcher.fetch()).willReturn(List.of());
    given(sportsDbFetcher.fetch()).willThrow(new SportsDbApiException(500));

    // when
    List<CollectResult> results = contentCollectService.collectAll();

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0)).isEqualTo(new CollectResult(ContentSource.TMDB, 0, 0, 0, 0, 0));
  }
}
