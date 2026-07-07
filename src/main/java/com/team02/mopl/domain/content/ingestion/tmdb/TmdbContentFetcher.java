package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.ExternalContentMapper;
import com.team02.mopl.domain.content.ingestion.exception.ExternalApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * TMDB popular 영화/드라마 수집기. 설정된 페이지 수만큼 순회하며, 페이지 단위 호출 실패는 warn 로그 후 나머지 페이지를 계속 수집한다.
 *
 * <p>장르 목록 조회 실패는 전파한다(인증/네트워크 문제라 이후 호출도 전부 실패할 상황이므로).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbContentFetcher implements ContentFetcher {

  private final TmdbClient tmdbClient;
  private final TmdbProperties properties;

  @Value("${app.storage.default-thumbnail-url:}")
  private String defaultThumbnailUrl;

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
            tmdbClient.fetchMovieGenres(), properties.imageBaseUrl(), defaultThumbnailUrl),
        results);
    collectPages(
        "/tv/popular",
        tmdbClient::fetchPopularTv,
        new TmdbTvMapper(
            tmdbClient.fetchTvGenres(), properties.imageBaseUrl(), defaultThumbnailUrl),
        results);
    return results;
  }

  private <T> void collectPages(
      String pathForLog,
      IntFunction<TmdbPageResponse<T>> pageFetcher,
      ExternalContentMapper<T> mapper,
      List<ExternalContentData> results) {
    for (int page = 1; page <= properties.pages(); page++) {
      try {
        pageFetcher.apply(page).results().stream()
            .map(mapper::map)
            .flatMap(Optional::stream)
            .forEach(results::add);
      } catch (ExternalApiException e) {
        log.warn("TMDB 페이지 수집 실패로 해당 페이지를 건너뜁니다. path={}, page={}", pathForLog, page, e);
      }
    }
  }
}
