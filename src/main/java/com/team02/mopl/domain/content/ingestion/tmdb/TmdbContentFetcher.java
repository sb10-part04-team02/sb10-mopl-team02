package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.ExternalContentMapper;
import com.team02.mopl.domain.content.ingestion.exception.ExternalApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// TMDB popular 영화/드라마 수집기
// 설정된 페이지 수만큼 순회하며, 페이지 단위 호출 실패는 warn 로그 후 나머지 페이지를 계속 수집
@Slf4j
@Component
public class TmdbContentFetcher implements ContentFetcher {

  private final TmdbClient tmdbClient;
  private final TmdbAgeRatingPolicy ageRatingPolicy;
  private final TmdbProperties properties;
  private final String defaultThumbnailUrl;

  public TmdbContentFetcher(
      TmdbClient tmdbClient,
      TmdbAgeRatingPolicy ageRatingPolicy,
      TmdbProperties properties,
      @Value("${app.storage.default-thumbnail-url:}") String defaultThumbnailUrl) {
    this.tmdbClient = tmdbClient;
    this.ageRatingPolicy = ageRatingPolicy;
    this.properties = properties;
    this.defaultThumbnailUrl = defaultThumbnailUrl;
  }

  @Override
  public ContentSource source() {
    return ContentSource.TMDB;
  }

  @Override
  public List<ExternalContentData> fetch() {
    List<ExternalContentData> results = new ArrayList<>();
    collectPages(
        "/movie/popular",
        tmdbClient::fetchPopularMovies,
        new TmdbMovieMapper(
            fetchGenresSafely(tmdbClient::fetchMovieGenres, "/genre/movie/list"),
            properties.imageBaseUrl(),
            defaultThumbnailUrl),
        raw -> ageRatingPolicy.isRestrictedMovie(raw.id()),
        results);
    collectPages(
        "/tv/popular",
        tmdbClient::fetchPopularTv,
        new TmdbTvMapper(
            fetchGenresSafely(tmdbClient::fetchTvGenres, "/genre/tv/list"),
            properties.imageBaseUrl(),
            defaultThumbnailUrl),
        raw -> ageRatingPolicy.isRestrictedTv(raw.id()),
        results);
    return results;
  }

  // 장르는 태그 부가정보일 뿐이므로 조회 실패가 콘텐츠 수집 본체를 중단시키지 않도록 빈 Map으로 폴백
  private Map<Integer, String> fetchGenresSafely(
      Supplier<Map<Integer, String>> genreFetcher, String pathForLog) {
    try {
      return genreFetcher.get();
    } catch (ExternalApiException e) {
      log.warn("TMDB 장르 조회 실패로 태그 없이 수집을 계속합니다. path={}", pathForLog, e);
      return Map.of();
    }
  }

  private <T> void collectPages(
      String pathForLog,
      IntFunction<TmdbPageResponse<T>> pageFetcher,
      ExternalContentMapper<T> mapper,
      Predicate<T> restricted,
      List<ExternalContentData> results) {
    for (int page = 1; page <= properties.pages(); page++) {
      try {
        for (T raw : pageFetcher.apply(page).results()) {
          // 등급 판정은 항목마다 추가 호출이 드므로, 매핑에서 걸러진 항목에는 호출을 낭비하지 않는다
          Optional<ExternalContentData> mapped = mapper.map(raw);
          if (mapped.isPresent() && !restricted.test(raw)) {
            results.add(mapped.get());
          }
        }
      } catch (ExternalApiException e) {
        log.warn("TMDB 페이지 수집 실패로 해당 페이지를 건너뜁니다. path={}, page={}", pathForLog, page, e);
      }
    }
  }
}
