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
// - 한 날짜에 데이터가 몰려(특정 날짜 데이터로 꽉 참) 커서가 안 내려가면 하루를 빼서 강제 전진한다
// - floor-date까지 내려가면 backfillComplete로 표시해 이후 실행은 건너뛴다
@Slf4j
@Component
public class TmdbBackfillContentFetcher implements ContentFetcher {

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

    LocalDate lte = state.cursorDate(); // null이면 상한 없이 최신부터 (첫 실행)
    LocalDate base = (lte != null) ? lte : LocalDate.now(); // 전진/정체 판정 기준
    LocalDate minSeen = null;
    int pagesPerRun = properties.backfill().pagesPerRun();

    for (int page = 1; page <= pagesPerRun; page++) {
      TmdbPageResponse<T> response;
      try {
        response = pageFetcher.apply(page, lte);
      } catch (ExternalApiException e) {
        log.warn(
            "discover backfill 페이지 수집 실패로 이번 매체를 중단합니다. mediaType={}, page={}", mediaType, page, e);
        break;
      }
      if (response.results().isEmpty()) {
        break; // 더 내려갈 데이터 없음
      }
      for (T item : response.results()) {
        mapper.map(item).ifPresent(results::add);
        LocalDate date = parseDate(rawDateExtractor.apply(item));
        if (date != null && (minSeen == null || date.isBefore(minSeen))) {
          minSeen = date; // 이번 실행에서 본 가장 오래된 개봉일
        }
      }
      if (page >= response.totalPages()) {
        break; // 마지막 페이지 도달
      }
    }

    advanceCursor(mediaType, base, minSeen);
  }

  // 커서를 이번 실행이 도달한 지점으로 전진시킨다.
  // - 유효 날짜가 없거나 진전이 없으면(minSeen >= base) 하루 빼서 강제 전진 (한 날짜 물량 정체 방어)
  // - 그 외에는 최소 개봉일로 이동 (inclusive)
  private void advanceCursor(TmdbMediaType mediaType, LocalDate base, LocalDate minSeen) {
    LocalDate floor = properties.backfill().floorDate();
    LocalDate next = (minSeen == null || !minSeen.isBefore(base)) ? base.minusDays(1) : minSeen;
    boolean backfillComplete = !next.isAfter(floor); // next <= floorDate
    cursorService.advance(mediaType, next, backfillComplete);
    log.info(
        "discover backfill 커서 전진. mediaType={}, nextCursorDate={}, backfillComplete={}",
        mediaType,
        next,
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
