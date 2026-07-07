package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbGenreDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbGenreListResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** TMDB HTTP 호출 캡슐화. 에러 응답은 RestClient 상태 핸들러가, IO 오류는 이 클래스가 TmdbApiException으로 변환한다. */
@Component
public class TmdbClient {

  private static final ParameterizedTypeReference<TmdbPageResponse<TmdbMovieDto>> MOVIE_PAGE_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<TmdbPageResponse<TmdbTvDto>> TV_PAGE_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final String language;

  public TmdbClient(@Qualifier("tmdbRestClient") RestClient restClient, TmdbProperties properties) {
    this.restClient = restClient;
    this.language = properties.language();
  }

  public TmdbPageResponse<TmdbMovieDto> fetchPopularMovies(int page) {
    return getPage("/movie/popular", page, MOVIE_PAGE_TYPE);
  }

  public TmdbPageResponse<TmdbTvDto> fetchPopularTv(int page) {
    return getPage("/tv/popular", page, TV_PAGE_TYPE);
  }

  public Map<Integer, String> fetchMovieGenres() {
    return fetchGenres("/genre/movie/list");
  }

  public Map<Integer, String> fetchTvGenres() {
    return fetchGenres("/genre/tv/list");
  }

  private <T> TmdbPageResponse<T> getPage(
      String path, int page, ParameterizedTypeReference<TmdbPageResponse<T>> responseType) {
    try {
      TmdbPageResponse<T> body =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(path)
                          .queryParam("language", language)
                          .queryParam("page", page)
                          .build())
              .retrieve()
              .body(responseType);
      return requireBody(body);
    } catch (RestClientException e) {
      throw new TmdbApiException(e);
    }
  }

  private Map<Integer, String> fetchGenres(String path) {
    try {
      TmdbGenreListResponse body =
          restClient
              .get()
              .uri(uriBuilder -> uriBuilder.path(path).queryParam("language", language).build())
              .retrieve()
              .body(TmdbGenreListResponse.class);
      return requireBody(body).genres().stream()
          .collect(Collectors.toMap(TmdbGenreDto::id, TmdbGenreDto::name));
    } catch (RestClientException e) {
      throw new TmdbApiException(e);
    }
  }

  private static <T> T requireBody(T body) {
    if (body == null) {
      throw new TmdbApiException(new IllegalStateException("TMDB 응답 본문이 비어 있습니다."));
    }
    return body;
  }
}
