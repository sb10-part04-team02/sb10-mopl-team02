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
import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
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

  // 포스터/줄거리가 없는 항목 (strict backfill에서 수집 제외 대상)
  private static TmdbMovieDto incompleteMovie(long id, String releaseDate) {
    return new TmdbMovieDto(id, "영화" + id, null, null, releaseDate, List.of());
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
  @DisplayName("커서가 없는 첫 실행은 오늘을 상한으로 조회한다")
  void backfill_firstRun_startsFromToday() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(null, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(1, movie(1, today.toString())));
    noTv();

    fetcher.fetch();

    // 상한이 null이면 미래 개봉일부터 내려와 커서가 어제로 밀리고, 그 사이 구간이 누락된다
    then(tmdbClient).should().discoverMovies(1, today);
    then(cursorService).should().advance(TmdbMediaType.MOVIE, today.minusDays(1), 1, false);
  }

  @Test
  @DisplayName("커서가 없으면 오늘부터 조회하고, 응답의 최소 개봉일로 커서를 전진시킨다")
  void backfill_firstRun_advancesCursorToMinDate() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(null, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    given(tmdbClient.discoverMovies(eq(1), any()))
        .willReturn(moviePage(9, movie(1, "2026-05-10"), movie(2, "2026-03-01")));
    given(tmdbClient.discoverMovies(eq(2), any()))
        .willReturn(moviePage(9, movie(3, "2026-02-20"), movie(4, "2026-01-15")));
    noTv();

    List<ExternalContentData> results = fetcher.fetch();

    assertThat(results).extracting(ExternalContentData::externalId).contains("movie:1", "movie:4");
    // 최소 개봉일 2026-01-15로 전진, floor보다 위이므로 미소진
    then(cursorService).should().advance(TmdbMediaType.MOVIE, LocalDate.of(2026, 1, 15), 1, false);
  }

  @Test
  @DisplayName("포스터/줄거리가 없는 항목은 수집하지 않지만 커서는 그 항목 개봉일까지 전진한다")
  void backfill_skipsIncompleteItemsButStillAdvancesCursor() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(null, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    // 2번은 포스터/줄거리 누락 -> 수집 제외, 하지만 가장 오래된 개봉일이라 커서 전진 기준이 됨
    given(tmdbClient.discoverMovies(eq(1), any()))
        .willReturn(moviePage(1, movie(1, "2026-05-10"), incompleteMovie(2, "2026-01-15")));
    noTv();

    List<ExternalContentData> results = fetcher.fetch();

    assertThat(results).extracting(ExternalContentData::externalId).containsExactly("movie:1");
    // 수집은 movie:1만, 커서는 누락 항목의 개봉일 2026-01-15까지 전진
    then(cursorService).should().advance(TmdbMediaType.MOVIE, LocalDate.of(2026, 1, 15), 1, false);
  }

  @Test
  @DisplayName("이미 backfill 완료된 매체는 조회 없이 건너뛴다")
  void backfill_whenBackfillComplete_skips() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(FLOOR, 1, true));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    noTv();

    fetcher.fetch();

    then(tmdbClient).should(never()).discoverMovies(anyInt(), any());
    then(cursorService)
        .should(never())
        .advance(eq(TmdbMediaType.MOVIE), any(), anyInt(), anyBoolean());
  }

  @Test
  @DisplayName("진전이 없고 마지막 페이지까지 다 읽었으면 하루 빼서 강제 전진한다")
  void backfill_whenStuckAndBoundExhausted_forcesProgressByOneDay() {
    LocalDate cursor = LocalDate.of(2020, 6, 1);
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(cursor, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    // 모든 항목이 정확히 커서 날짜라 minSeen == base. total_pages=1이라 더 읽을 것도 없다
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(1, movie(1, "2020-06-01"), movie(2, "2020-06-01")));
    noTv();

    fetcher.fetch();

    then(cursorService).should().advance(TmdbMediaType.MOVIE, cursor.minusDays(1), 1, false);
  }

  @Test
  @DisplayName("진전이 없어도 안 읽은 페이지가 남았으면 날짜를 유지하고 다음 페이지부터 이어 읽는다")
  void backfill_whenStuckButPagesRemain_keepsDateAndAdvancesPage() {
    LocalDate cursor = LocalDate.of(2020, 6, 1);
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(cursor, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    // 한 날짜에 물량이 몰린 상황: 2페이지(pages-per-run)를 다 읽어도 total_pages=9라 아직 남았다
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(9, movie(1, "2020-06-01"), movie(2, "2020-06-01")));
    noTv();

    fetcher.fetch();

    // 하루 내리면 3페이지 이후가 영구 누락되므로, 날짜는 그대로 두고 시작 페이지만 3으로 올린다
    then(cursorService).should().advance(TmdbMediaType.MOVIE, cursor, 3, false);
  }

  @Test
  @DisplayName("커서에 남은 시작 페이지가 있으면 그 페이지부터 이어 읽는다")
  void backfill_whenCursorHasNextPage_resumesFromThatPage() {
    LocalDate cursor = LocalDate.of(2020, 6, 1);
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(cursor, 3, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(9, movie(1, "2020-06-01")));
    noTv();

    fetcher.fetch();

    then(tmdbClient).should().discoverMovies(3, cursor);
    then(tmdbClient).should().discoverMovies(4, cursor); // pages-per-run=2 -> 3~4페이지
    then(tmdbClient).should(never()).discoverMovies(1, cursor);
    then(cursorService).should().advance(TmdbMediaType.MOVIE, cursor, 5, false);
  }

  @Test
  @DisplayName("이어 읽던 중 날짜가 내려가면 커서 날짜를 옮기고 시작 페이지를 1로 되돌린다")
  void backfill_whenResumedRunProgresses_resetsNextPage() {
    LocalDate cursor = LocalDate.of(2020, 6, 1);
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(cursor, 3, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    // 3페이지에서 드디어 커서보다 과거 날짜가 나왔다 -> 남은 항목은 모두 그 날짜 이하라 재조회로 커버된다
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(9, movie(1, "2020-06-01"), movie(2, "2020-05-28")));
    noTv();

    fetcher.fetch();

    then(cursorService).should().advance(TmdbMediaType.MOVIE, LocalDate.of(2020, 5, 28), 1, false);
  }

  @Test
  @DisplayName("이어 읽기가 TMDB 페이지 상한아에 닿으면 남은 항목을 포기하고 하루 전진한다")
  void backfill_whenReachesMaxDiscoverPage_forcesProgress() {
    LocalDate cursor = LocalDate.of(2020, 6, 1);
    // 시작 페이지 499 + pages-per-run 2 -> 499~500에서 멈춘다 (501부터는 TMDB가 거부)
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(cursor, 499, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(9999, movie(1, "2020-06-01")));
    noTv();

    fetcher.fetch();

    then(tmdbClient).should().discoverMovies(500, cursor);
    then(tmdbClient).should(never()).discoverMovies(501, cursor);
    then(cursorService).should().advance(TmdbMediaType.MOVIE, cursor.minusDays(1), 1, false);
  }

  @Test
  @DisplayName("최소 개봉일이 floor-date 이하로 내려가면 backfillComplete로 표시한다")
  void backfill_whenReachesFloor_marksBackfillComplete() {
    given(cursorService.read(TmdbMediaType.MOVIE))
        .willReturn(new CursorState(LocalDate.of(1950, 2, 1), 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    given(tmdbClient.discoverMovies(anyInt(), any()))
        .willReturn(moviePage(9, movie(1, "1950-01-05"), movie(2, "1949-12-20")));
    noTv();

    fetcher.fetch();

    ArgumentCaptor<Boolean> backfillComplete = ArgumentCaptor.forClass(Boolean.class);
    then(cursorService)
        .should()
        .advance(
            eq(TmdbMediaType.MOVIE),
            eq(LocalDate.of(1949, 12, 20)),
            eq(1),
            backfillComplete.capture());
    assertThat(backfillComplete.getValue()).isTrue();
  }

  @Test
  @DisplayName("total_pages에 도달하면 pages-per-run 이전이라도 조기 종료한다")
  void backfill_stopsAtTotalPages() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(null, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    // total_pages=1이므로 1페이지만 조회하고 2페이지는 호출하지 않아야 한다
    given(tmdbClient.discoverMovies(eq(1), any())).willReturn(moviePage(1, movie(1, "2026-05-10")));
    noTv();

    fetcher.fetch();

    then(tmdbClient).should().discoverMovies(eq(1), any());
    then(tmdbClient).should(never()).discoverMovies(eq(2), any());
  }

  @Test
  @DisplayName("첫 페이지 조회가 실패해 아무 데이터도 못 얻으면 커서를 전진시키지 않는다")
  void backfill_whenFirstPageFails_keepsCursor() {
    LocalDate cursor = LocalDate.of(2020, 6, 1);
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(cursor, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    // 첫 페이지부터 API 실패 -> 확보한 개봉일이 없으므로 강제 전진 시 실패 구간이 영구 누락된다
    given(tmdbClient.discoverMovies(eq(1), any()))
        .willThrow(new TmdbApiException(new RuntimeException("일시 오류")));
    noTv();

    fetcher.fetch();

    then(cursorService)
        .should(never())
        .advance(eq(TmdbMediaType.MOVIE), any(), anyInt(), anyBoolean());
  }

  @Test
  @DisplayName("후속 페이지에서 실패해도 앞 페이지에서 데이터를 얻었으면 최소 개봉일로 전진한다")
  void backfill_whenLaterPageFailsButDataSeen_advances() {
    given(cursorService.read(TmdbMediaType.MOVIE)).willReturn(new CursorState(null, 1, false));
    given(cursorService.read(TmdbMediaType.TV)).willReturn(new CursorState(null, 1, false));
    given(tmdbClient.discoverMovies(eq(1), any()))
        .willReturn(moviePage(9, movie(1, "2026-05-10"), movie(2, "2026-03-01")));
    given(tmdbClient.discoverMovies(eq(2), any()))
        .willThrow(new TmdbApiException(new RuntimeException("일시 오류")));
    noTv();

    fetcher.fetch();

    // 1페이지에서 본 최소 개봉일 2026-03-01로 정상 전진 (실패해도 확보한 데이터는 반영)
    then(cursorService).should().advance(TmdbMediaType.MOVIE, LocalDate.of(2026, 3, 1), 1, false);
  }

  @Test
  @DisplayName("source는 TMDB를 반환한다")
  void source_returnsTmdb() {
    assertThat(fetcher.source()).isEqualTo(ContentSource.TMDB);
  }
}
