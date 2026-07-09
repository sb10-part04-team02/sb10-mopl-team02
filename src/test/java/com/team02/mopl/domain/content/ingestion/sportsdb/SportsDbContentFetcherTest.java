package com.team02.mopl.domain.content.ingestion.sportsdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.exception.SportsDbApiException;
import com.team02.mopl.domain.content.ingestion.sportsdb.SportsDbProperties.League;
import com.team02.mopl.domain.content.ingestion.sportsdb.dto.SportsDbEventDto;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SportsDbContentFetcherTest {

  @Mock private SportsDbClient sportsDbClient;

  private SportsDbContentFetcher fetcher;

  @BeforeEach
  void setUp() {
    SportsDbProperties properties =
        new SportsDbProperties(
            "123",
            "https://www.thesportsdb.com/api/v1/json",
            Duration.ofSeconds(3),
            Duration.ofSeconds(10),
            List.of(new League("4328", "2025-2026"), new League("4387", "2025-2026")));
    fetcher = new SportsDbContentFetcher(sportsDbClient, properties, "");
  }

  private static SportsDbEventDto event(String idEvent, String strEvent) {
    return new SportsDbEventDto(
        idEvent, strEvent, null, "Soccer", "리그", "2025-2026", null, null, null, null);
  }

  @Test
  @DisplayName("source는 SPORTS_DB를 반환한다")
  void source_returnsSportsDb() {
    assertThat(fetcher.source()).isEqualTo(ContentSource.SPORTS_DB);
  }

  @Test
  @DisplayName("설정된 리그 목록을 순회해 매핑 결과를 합산한다")
  void fetch_collectsConfiguredLeagues() {
    // given
    given(sportsDbClient.fetchSeasonEvents("4328", "2025-2026"))
        .willReturn(List.of(event("1", "Liverpool vs Bournemouth")));
    given(sportsDbClient.fetchSeasonEvents("4387", "2025-2026"))
        .willReturn(List.of(event("2", "Lakers vs Celtics"), event("3", "Bulls vs Knicks")));

    // when
    List<ExternalContentData> results = fetcher.fetch();

    // then
    assertThat(results).hasSize(3);
    assertThat(results)
        .extracting(ExternalContentData::externalId)
        .containsExactly("event:1", "event:2", "event:3");
    assertThat(results).allMatch(data -> data.source() == ContentSource.SPORTS_DB);
  }

  @Test
  @DisplayName("리그 단위 호출이 실패해도 나머지 리그는 계속 수집한다 (리그 단위 격리)")
  void fetch_whenLeagueFails_continuesWithRemainingLeagues() {
    // given - 첫 리그는 실패, 둘째 리그는 정상
    given(sportsDbClient.fetchSeasonEvents("4328", "2025-2026"))
        .willThrow(new SportsDbApiException(500));
    given(sportsDbClient.fetchSeasonEvents("4387", "2025-2026"))
        .willReturn(List.of(event("2", "Lakers vs Celtics")));

    // when
    List<ExternalContentData> results = fetcher.fetch();

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).externalId()).isEqualTo("event:2");
  }

  @Test
  @DisplayName("매핑에서 걸러진 항목(필수값 누락)은 결과에 포함하지 않는다")
  void fetch_excludesInvalidEvents() {
    // given - 둘째 이벤트는 경기명 누락
    given(sportsDbClient.fetchSeasonEvents("4328", "2025-2026"))
        .willReturn(List.of(event("1", "Liverpool vs Bournemouth"), event("2", null)));
    given(sportsDbClient.fetchSeasonEvents("4387", "2025-2026")).willReturn(List.of());

    // when
    List<ExternalContentData> results = fetcher.fetch();

    // then
    assertThat(results).hasSize(1);
    assertThat(results.get(0).externalId()).isEqualTo("event:1");
  }
}
