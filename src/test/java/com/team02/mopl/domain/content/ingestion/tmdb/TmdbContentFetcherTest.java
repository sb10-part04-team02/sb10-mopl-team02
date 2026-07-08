package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TmdbContentFetcherTest {

  @Mock private TmdbClient tmdbClient;

  private TmdbContentFetcher fetcher;

  @BeforeEach
  void setUp() {
    TmdbProperties properties =
        new TmdbProperties(
            "token",
            "https://api.themoviedb.org/3",
            "https://image.tmdb.org/t/p/w500",
            "ko-KR",
            2,
            Duration.ofSeconds(3),
            Duration.ofSeconds(10));
    fetcher = new TmdbContentFetcher(tmdbClient, properties);
    ReflectionTestUtils.setField(fetcher, "defaultThumbnailUrl", "");
  }

  private void givenGenres() {
    given(tmdbClient.fetchMovieGenres()).willReturn(Map.of(28, "액션"));
    given(tmdbClient.fetchTvGenres()).willReturn(Map.of(10765, "SF"));
  }

  private static TmdbPageResponse<TmdbMovieDto> moviePage(TmdbMovieDto... movies) {
    return new TmdbPageResponse<>(1, List.of(movies), 100);
  }

  private static TmdbPageResponse<TmdbTvDto> tvPage(TmdbTvDto... tvs) {
    return new TmdbPageResponse<>(1, List.of(tvs), 100);
  }

  @Test
  @DisplayName("설정된 페이지 수만큼 영화/드라마를 조회해 매핑 결과를 합산한다")
  void fetch_collectsConfiguredPagesForMoviesAndTv() {
    // given
    givenGenres();
    given(tmdbClient.fetchPopularMovies(1))
        .willReturn(moviePage(new TmdbMovieDto(1, "영화1", "줄거리", "/p1.jpg", List.of(28))));
    given(tmdbClient.fetchPopularMovies(2))
        .willReturn(moviePage(new TmdbMovieDto(2, "영화2", "줄거리", "/p2.jpg", List.of())));
    given(tmdbClient.fetchPopularTv(1))
        .willReturn(tvPage(new TmdbTvDto(3, "드라마1", "줄거리", "/p3.jpg", List.of(10765))));
    given(tmdbClient.fetchPopularTv(2)).willReturn(tvPage());

    // when
    List<ExternalContentData> results = fetcher.fetch();

    // then - 영화 2건 + 드라마 1건 = 총 3건 수집 되어야 함
    assertThat(results).hasSize(3);
    assertThat(results).extracting(ExternalContentData::externalId).containsExactly("1", "2", "3");
    then(tmdbClient).should().fetchPopularMovies(1);
    then(tmdbClient).should().fetchPopularMovies(2);
    then(tmdbClient).should().fetchPopularTv(1);
    then(tmdbClient).should().fetchPopularTv(2);
  }

  @Test
  @DisplayName("매핑에 실패한 항목(필수 값 누락)은 결과에서 제외한다")
  void fetch_excludesItemsThatFailMapping() {
    // given
    givenGenres();
    given(tmdbClient.fetchPopularMovies(1))
        .willReturn(
            moviePage(
                new TmdbMovieDto(1, "정상 영화", "줄거리", "/p1.jpg", List.of()),
                new TmdbMovieDto(2, " ", "제목 없는 영화", "/p2.jpg", List.of())));
    given(tmdbClient.fetchPopularMovies(2)).willReturn(moviePage());
    given(tmdbClient.fetchPopularTv(1)).willReturn(tvPage());
    given(tmdbClient.fetchPopularTv(2)).willReturn(tvPage());

    // when
    List<ExternalContentData> results = fetcher.fetch();

    // then
    assertThat(results).extracting(ExternalContentData::externalId).containsExactly("1");
  }

  @Test
  @DisplayName("특정 페이지 조회가 실패해도 나머지 페이지 수집을 계속한다")
  void fetch_continuesAfterPageFailure() {
    // given - 영화 1페이지 조회 시 예외를 던지도록 설정 (일시적 API 오류 상황 재현)
    givenGenres();
    given(tmdbClient.fetchPopularMovies(1))
        .willThrow(new TmdbApiException(new RuntimeException("일시 오류")));
    given(tmdbClient.fetchPopularMovies(2))
        .willReturn(moviePage(new TmdbMovieDto(2, "영화2", "줄거리", "/p2.jpg", List.of())));
    given(tmdbClient.fetchPopularTv(1))
        .willReturn(tvPage(new TmdbTvDto(3, "드라마1", "줄거리", "/p3.jpg", List.of())));
    given(tmdbClient.fetchPopularTv(2)).willReturn(tvPage());

    // when
    List<ExternalContentData> results = fetcher.fetch();

    // then - 실패한 영화 1페이지는 건너뛰고, 성공한 2, 3만 수집
    assertThat(results).extracting(ExternalContentData::externalId).containsExactly("2", "3");
  }

  @Test
  @DisplayName("source는 TMDB를 반환한다")
  void source_returnsTmdb() {
    assertThat(fetcher.source()).isEqualTo(ContentSource.TMDB);
  }
}
