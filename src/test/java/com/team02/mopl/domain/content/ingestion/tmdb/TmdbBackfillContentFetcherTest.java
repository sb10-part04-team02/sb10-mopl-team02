package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.backfill.BackfillCursorService;
import com.team02.mopl.domain.content.ingestion.backfill.BackfillCursorService.CursorState;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TmdbBackfillContentFetcherTest {

  private static final LocalDate FLOOR = LocalDate.of(1950, 1, 1);

  @Mock private TmdbClient tmdbClient;
  @Mock private BackfillCursorService cursorService;

  private TmdbBackfillContentFetcher fetcher;

  private static TmdbPageResponse<TmdbMovieDto> moviePage(int totalPages, TmdbMovieDto... movies) {
    return new TmdbPageResponse<>(1, List.of(movies), totalPages);
  }

  private static TmdbPageResponse<TmdbTvDto> tvPage(int totalPages, TmdbTvDto... tvs) {
    return new TmdbPageResponse<>(1, List.of(tvs), totalPages);
  }

  private static TmdbMovieDto movie(long id, String releaseDate) {
    return new TmdbMovieDto(id, "영화" + id, "줄거리", "/p.jpg", releaseDate, List.of());
  }

  @BeforeEach
  void setUp() {
    TmdbProperties properties =
        new TmdbProperties(
            "token",
            "https://api.themoviedb.org/3",
            "https://image.tmdb.org/t/p/w500",
            "ko-KR",
            2,
            new TmdbProperties.Backfill(2, FLOOR), // pages-per-run=2
            Duration.ofSeconds(3),
            Duration.ofSeconds(10));
    fetcher = new TmdbBackfillContentFetcher(tmdbClient, properties, cursorService, "");
    // 매퍼는 backfill 진입 전 항상 생성되므로 장르 조회는 매 fetch()에서 호출된다
    lenient().when(tmdbClient.fetchMovieGenres()).thenReturn(Map.of());
    lenient().when(tmdbClient.fetchTvGenres()).thenReturn(Map.of());
  }

  private void noTv() {
    given(tmdbClient.discoverTv(anyInt(), any())).willReturn(tvPage(1));
  }

  @Test
  @DisplayName("커서가 없으면 상한 없이 최신부터 조회하고, 응답의 최소 개봉일로 커서를 전진시킨다")
  void backfill_firstRun_advancesCursorToMinDate() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(null, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, false));
    given(tmdbClient.discoverMovies(eq(1), any()))
        .willReturn(moviePage(9, movie(1, "2026-05-10"), movie(2, "2026-03-01")));
    given(tmdbClient.discoverMovies(eq(2), any()))
        .willReturn(moviePage(9, movie(3, "2026-02-20"), movie(4, "2026-01-15")));
    noTv();

    List<ExternalContentData> results = fetcher.fetch();

    assertThat(results).extracting(ExternalContentData::externalId).contains("movie:1", "movie:4");
    // 최소 개봉일 2026-01-15로 전진, floor보다 위이므로 미소진
    then(cursorService).should().advance(TmdbMediaType.MOVIE, LocalDate.of(2026, 1, 15), false);
  }

  @Test
  @DisplayName("이미 백필 완료된 매체는 조회 없이 건너뛴다")
  void backfill_whenBackfillComplete_skips() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(FLOOR, true));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, false));
    noTv();

    fetcher.fetch();

    then(tmdbClient).should(never()).discoverMovies(anyInt(), any());
    then(cursorService).should(never()).advance(eq(TmdbMediaType.MOVIE), any(), anyBoolean());
  }

  @Test
  @DisplayName("최소 개봉일이 커서와 같아 진전이 없으면 하루 빼서 강제 전진한다")
  void backfill_whenStuck_forcesProgressByOneDay() {
    LocalDate cursor = LocalDate.of(2020, 6, 1);
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(cursor, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, false));
    // 모든 항목이 정확히 커서 날짜라 minSeen == base
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(9, movie(1, "2020-06-01"), movie(2, "2020-06-01")));
    noTv();

    fetcher.fetch();

    then(cursorService).should().advance(TmdbMediaType.MOVIE, cursor.minusDays(1), false);
  }

  @Test
  @DisplayName("최소 개봉일이 floor-date 이하로 내려가면 backfillComplete로 표시한다")
  void backfill_whenReachesFloor_marksBackfillComplete() {
    given(cursorService.read(TmdbMediaType.MOVIE))
        .willReturn(new CursorState(LocalDate.of(1950, 2, 1), false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, false));
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(9, movie(1, "1950-01-05"), movie(2, "1949-12-20")));
    noTv();

    fetcher.fetch();

    ArgumentCaptor<Boolean> backfillComplete = ArgumentCaptor.forClass(Boolean.class);
    then(cursorService)
        .should()
        .advance(
            eq(TmdbMediaType.MOVIE), eq(LocalDate.of(1949, 12, 20)), backfillComplete.capture());
    assertThat(backfillComplete.getValue()).isTrue();
  }

  @Test
  @DisplayName("total_pages에 도달하면 pages-per-run 이전이라도 조기 종료한다")
  void backfill_stopsAtTotalPages() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(null, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, false));
    // total_pages=1이므로 1페이지만 조회하고 2페이지는 호출하지 않아야 한다
    given(tmdbClient.discoverMovies(eq(1), any())).willReturn(moviePage(1, movie(1, "2026-05-10")));
    noTv();

    fetcher.fetch();

    then(tmdbClient).should().discoverMovies(eq(1), any());
    then(tmdbClient).should(never()).discoverMovies(eq(2), any());
  }

  @Test
  @DisplayName("source는 TMDB를 반환한다")
  void source_returnsTmdb() {
    assertThat(fetcher.source()).isEqualTo(ContentSource.TMDB);
  }
}
