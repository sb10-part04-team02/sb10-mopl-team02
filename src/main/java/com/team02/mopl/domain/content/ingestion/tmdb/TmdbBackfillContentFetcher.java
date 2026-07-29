package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.ExternalContentMapper;
import com.team02.mopl.domain.content.ingestion.backfill.BackfillCursorService;
import com.team02.mopl.domain.content.ingestion.backfill.BackfillCursorService.CursorState;
import com.team02.mopl.domain.content.ingestion.exception.ExternalApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// TMDB discover backfill 수집기
// - 매체(movie/tv)별로 개봉일 내림차순 상한(lte=커서)부터 pages-per-run 페이지를 훑고,
//   응답의 최소 개봉일을 다음 커서로 저장해 매 실행 과거로 한 칸씩 내려간다
// - 경계 날짜는 inclusive로 다시 요청한다
// - 한 날짜에 데이터가 몰려(특정 날짜 데이터로 꽉 참) 커서가 안 내려가면, 그 날짜에 아직 안 읽은 페이지가 남았는지에 따라 갈린다.
//   남았으면 날짜를 유지한 채 다음 시작 페이지만 올려 이어 읽고, 다 읽었으면(마지막 페이지 도달) 하루를 빼서 강제 전진한다.
// - 단, 첫 페이지부터 fetch가 실패해 아무 데이터도 못 얻으면 커서를 유지해 다음 실행에서 같은 지점을 재시도한다
// - floor-date까지 내려가면 backfillComplete로 표시해 이후 실행은 건너뛴다
@Slf4j
@Component
public class TmdbBackfillContentFetcher implements ContentFetcher {

  // 첫 실행의 시작 상한(오늘) 기준 타임존. 스케줄러(@Scheduled zone)와 같은 값으로 맞춘다
  private static final ZoneId INGESTION_ZONE = ZoneId.of("Asia/Seoul");

  // TMDB discover의 page 상한. 초과 요청은 400을 받으므로 이어 읽기도 여기서 멈춘다
  private static final int MAX_DISCOVER_PAGE = 500;

  private final TmdbClient tmdbClient;
  private final TmdbProperties properties;
  private final BackfillCursorService cursorService;
  private final String defaultThumbnailUrl;

  public TmdbBackfillContentFetcher(
      TmdbClient tmdbClient,
      TmdbProperties properties,
      BackfillCursorService cursorService,
      @Value("${app.storage.default-thumbnail-url:}") String defaultThumbnailUrl) {
    this.tmdbClient = tmdbClient;
    this.properties = properties;
    this.cursorService = cursorService;
    this.defaultThumbnailUrl = defaultThumbnailUrl;
  }

  private static LocalDate parseDate(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(raw); // TMDB 개봉일은 ISO yyyy-MM-dd
    } catch (DateTimeParseException e) {
      return null; // 파싱 실패는 무시 (항목 자체는 이미 수집됨)
    }
  }

  @Override
  public ContentSource source() {
    return ContentSource.TMDB;
  }

  @Override
  public List<ExternalContentData> fetch() {
    List<ExternalContentData> results = new ArrayList<>();
    backfill(
        TmdbMediaType.MOVIE,
        tmdbClient::discoverMovies,
        TmdbMovieDto::releaseDate,
        new TmdbMovieMapper(
            fetchGenresSafely(tmdbClient::fetchMovieGenres, "/genre/movie/list"),
            properties.imageBaseUrl(),
            defaultThumbnailUrl,
            true), // 썸네일/줄거리가 모두 있는 항목만 수집
        results);
    backfill(
        TmdbMediaType.TV,
        tmdbClient::discoverTv,
        TmdbTvDto::firstAirDate,
        new TmdbTvMapper(
            fetchGenresSafely(tmdbClient::fetchTvGenres, "/genre/tv/list"),
            properties.imageBaseUrl(),
            defaultThumbnailUrl,
            true), // 썸네일/줄거리가 모두 있는 항목만 수집
        results);
    return results;
  }

  // 매체 1개의 backfill 1회: 커서 상한 이하에서 pages-per-run 페이지를 훑고 커서를 전진시킨다.
  private <T> void backfill(
      TmdbMediaType mediaType,
      BiFunction<Integer, LocalDate, TmdbPageResponse<T>> pageFetcher,
      Function<T, String> rawDateExtractor,
      ExternalContentMapper<T> mapper,
      List<ExternalContentData> results) {

    CursorState state = cursorService.read(mediaType);
    if (state.backfillComplete()) {
      log.info("discover backfill이 완료된 매체라 건너뜁니다. mediaType={}", mediaType);
      return;
    }

    // 커서가 없는 첫 실행도 오늘을 상한으로 둔다. 상한을 비우면 개봉 예정작(미래 날짜)부터 내려오는데,
    // 그 날짜는 판정 기준인 오늘보다 뒤라 advanceCursor가 진전 없음으로 보고 커서를 어제로 내린다.
    // 커서는 과거로만 가므로 그 사이 구간(오늘 ~ 미래 개봉작)은 이후 어느 실행에서도 조회되지 않는다.
    LocalDate lte =
        (state.cursorDate() != null) ? state.cursorDate() : LocalDate.now(INGESTION_ZONE);
    LocalDate minSeen = null;
    int startPage = state.nextPage(); // 직전 실행이 같은 날짜에서 끊겼으면 그 다음 페이지부터
    int endPage = Math.min(startPage + properties.backfill().pagesPerRun() - 1, MAX_DISCOVER_PAGE);
    boolean fetchFailed = false;
    boolean exhaustedBound = false; // 이 lte 조건의 결과를 끝까지 다 읽었는지 (페이지 예산 소진과 구분)
    int lastReadPage = startPage - 1; // 실제로 읽어낸 마지막 페이지
    int seen = 0; // 응답으로 받은 항목 수 (수집 제외분 포함)
    int collectedBefore = results.size(); // results는 매체 간 누적이라 이번 매체 몫만 세려면 기준점이 필요

    for (int page = startPage; page <= endPage; page++) {
      TmdbPageResponse<T> response;
      try {
        response = pageFetcher.apply(page, lte);
      } catch (ExternalApiException e) {
        log.warn(
            "discover backfill 페이지 수집 실패로 이번 매체를 중단합니다. mediaType={}, page={}", mediaType, page, e);
        fetchFailed = true;
        break;
      }
      if (response.results().isEmpty()) {
        exhaustedBound = true; // 더 내려갈 데이터 없음
        break;
      }
      lastReadPage = page;
      for (T item : response.results()) {
        seen++;
        mapper.map(item).ifPresent(results::add);
        LocalDate date = parseDate(rawDateExtractor.apply(item));
        if (date != null && (minSeen == null || date.isBefore(minSeen))) {
          minSeen = date; // 이번 실행에서 본 가장 오래된 개봉일
        }
      }
      if (page >= response.totalPages()) {
        exhaustedBound = true; // 마지막 페이지 도달
        break;
      }
    }

    // 항목별 제외 사유는 debug라, 매체별 결과는 이 한 줄로 요약한다.
    // excluded가 seen에 육박하면 strict 필터(포스터/줄거리 필수)가 과한지 의심해볼 지점이다
    int collected = results.size() - collectedBefore;
    log.info(
        "discover backfill 매체 조회 완료. mediaType={}, lte={}, pages={}~{}, seen={}, collected={},"
            + " excluded={}",
        mediaType,
        lte,
        startPage,
        lastReadPage,
        seen,
        collected,
        seen - collected);

    // fetch 실패로 아무 날짜도 확보하지 못했으면 커서를 유지해 다음 실행에서 같은 지점부터 재시도한다.
    // (강제 전진하면 실패 구간의 콘텐츠가 영구 누락된다. 데이터 소진과 달리 재조회 대상이 남아 있다)
    if (fetchFailed && minSeen == null) {
      log.warn("discover backfill 수집 실패로 커서를 유지합니다. mediaType={}", mediaType);
      return;
    }

    advanceCursor(mediaType, lte, minSeen, exhaustedBound, lastReadPage);
  }

  // 커서를 이번 실행이 도달한 지점으로 전진시킨다.
  // - 최소 개봉일이 상한보다 과거면(정상 진행) 그 날짜로 이동하고 페이지는 1로 리셋한다.
  //   내림차순이라 아직 안 읽은 항목은 모두 minSeen 이하이므로 inclusive 재조회로 커버된다
  // - 진전이 없는데(minSeen >= base) 아직 안 읽은 페이지가 남았으면 날짜를 유지하고 다음 페이지부터 이어 읽는다
  // - 진전이 없고 그 상한의 결과를 다 읽었으면(또는 페이지 상한에 걸리면) 하루 빼서 강제 전진한다
  private void advanceCursor(
      TmdbMediaType mediaType,
      LocalDate base,
      LocalDate minSeen,
      boolean exhaustedBound,
      int lastReadPage) {

    LocalDate next;
    int nextPage;
    if (minSeen != null && minSeen.isBefore(base)) {
      next = minSeen;
      nextPage = 1;
    } else if (!exhaustedBound && lastReadPage < MAX_DISCOVER_PAGE) {
      // 한 날짜에 물량이 몰려 커서가 못 내려간 상태. 남은 페이지를 다음 실행이 이어 읽는다
      next = base;
      nextPage = lastReadPage + 1;
      log.info(
          "discover backfill 커서 날짜 유지, 다음 실행이 이어 읽습니다. mediaType={}, cursorDate={}, nextPage={}",
          mediaType,
          next,
          nextPage);
    } else {
      if (!exhaustedBound) {
        // deep paging 상한까지 읽고도 같은 날짜라 더 못 판다. 남은 항목은 포기하고 전진
        log.warn(
            "discover backfill이 페이지 상한에 걸려 남은 항목을 건너뜁니다. mediaType={}, cursorDate={}, page={}",
            mediaType,
            base,
            MAX_DISCOVER_PAGE);
      }
      next = base.minusDays(1);
      nextPage = 1;
    }

    LocalDate floor = properties.backfill().floorDate();
    boolean backfillComplete = !next.isAfter(floor); // next <= floorDate
    cursorService.advance(mediaType, next, nextPage, backfillComplete);
    log.info(
        "discover backfill 커서 전진. mediaType={}, nextCursorDate={}, nextPage={}, backfillComplete={}",
        mediaType,
        next,
        nextPage,
        backfillComplete);
  }

  // 장르는 태그 부가정보라 조회 실패가 수집 본체를 막지 않도록 빈 Map으로 폴백 (TmdbContentFetcher와 동일)
  private Map<Integer, String> fetchGenresSafely(
      Supplier<Map<Integer, String>> genreFetcher, String pathForLog) {
    try {
      return genreFetcher.get();
    } catch (ExternalApiException e) {
      log.warn("TMDB 장르 조회 실패로 태그 없이 backfill을 계속합니다. path={}", pathForLog, e);
      return Map.of();
    }
  }
}
