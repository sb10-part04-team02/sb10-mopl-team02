package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbContentRatingsResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbGenreDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbGenreListResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbReleaseDatesResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// TMDB HTTP 호출 캡슐화. 에러 응답은 RestClient 상태 핸들러가, IO 오류는 이 클래스가 TmdbApiException으로 변환
@Slf4j
@Component
public class TmdbClient {

  // - 제네릭 타입 정보가 런타임에 사라짐으로 TmdbPageResponse<T>의 T를 RestClient가 알 수 있도록 익명 서브클래스로 타입 정보를 보존
  // - 클래스 상속 관계에 선언된 제네릭 정보는 소거 되지 않음. 제네릭 시그니처를 리플렉션으로 읽을 수 있는 형태로 남는 것을 이용함
  private static final ParameterizedTypeReference<TmdbPageResponse<TmdbMovieDto>> MOVIE_PAGE_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<TmdbPageResponse<TmdbTvDto>> TV_PAGE_TYPE =
      new ParameterizedTypeReference<>() {};

  private static final int MAX_ATTEMPTS = 3; // 최초 호출 포함 총 시도 횟수
  private static final long BASE_BACKOFF_MILLIS = 200L; // 지수 백오프 기준: 200, 400ms ...

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

  // 국가별 관람 등급은 목록 API에 없어 항목별로 조회해야 한다
  public TmdbReleaseDatesResponse fetchMovieReleaseDates(long movieId) {
    return getById("/movie/" + movieId + "/release_dates", TmdbReleaseDatesResponse.class);
  }

  public TmdbContentRatingsResponse fetchTvContentRatings(long seriesId) {
    return getById("/tv/" + seriesId + "/content_ratings", TmdbContentRatingsResponse.class);
  }

  public Map<Integer, String> fetchMovieGenres() {
    return fetchGenres("/genre/movie/list");
  }

  public Map<Integer, String> fetchTvGenres() {
    return fetchGenres("/genre/tv/list");
  }

  private <T> TmdbPageResponse<T> getPage(
      String path, int page, ParameterizedTypeReference<TmdbPageResponse<T>> responseType) {
    return fetch(
        () ->
            requireBody(
                restClient
                    .get() // GET 요청 빌더 시작
                    .uri(
                        uriBuilder -> // URI 조합
                        uriBuilder
                                .path(path)
                                .queryParam("language", language)
                                .queryParam("page", page)
                                .build())
                    .retrieve() // 실제 HTTP 요청을 보내고 응답을 받아옴
                    .body(responseType))); // 역직렬화 + null 체크
  }

  // 등급 조회용. 등급 코드는 언어에 따라 달라지지 않으므로 language 파라미터를 붙이지 않는다
  private <T> T getById(String path, Class<T> responseType) {
    return fetch(
        () ->
            requireBody(
                restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder.path(path).build())
                    .retrieve()
                    .body(responseType)));
  }

  private Map<Integer, String> fetchGenres(String path) {
    TmdbGenreListResponse body =
        fetch(
            () ->
                requireBody(
                    restClient
                        .get()
                        .uri(
                            uriBuilder ->
                                uriBuilder.path(path).queryParam("language", language).build())
                        .retrieve()
                        .body(TmdbGenreListResponse.class)));
    return body.genres().stream()
        .collect(Collectors.toMap(TmdbGenreDto::id, TmdbGenreDto::name)); // Map으로 변환
  }

  // 페이지/장르 호출 공통 재시도. 일시적 장애(5xx, IO/타임아웃)만 지수 백오프로 재시도하고
  // 4xx 등 비일시적 오류는 즉시 던져 불필요한 재시도를 피한다. IO 오류는 TmdbApiException으로 래핑.
  private <T> T fetch(Supplier<T> operation) {
    int attempt = 1;
    while (true) {
      try {
        return operation.get(); // 성공하면 즉시 반환
      } catch (RestClientException e) { // IO/타임아웃 등 상태 핸들러가 잡지 못한 오류
        if (attempt >= MAX_ATTEMPTS) {
          throw new TmdbApiException(e);
        }
        // 재시도 전 기록. 메시지 대신 클래스명만 남김
        log.warn(
            "TMDB 호출 실패로 재시도합니다. attempt={}/{}, cause={}",
            attempt,
            MAX_ATTEMPTS,
            e.getClass().getSimpleName());
      } catch (TmdbApiException e) { // 상태 핸들러가 던진 4xx/5xx
        if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
          throw e;
        }
        // details는 statusCode만 담긴다
        log.warn(
            "TMDB 호출 실패로 재시도합니다. attempt={}/{}, details={}", attempt, MAX_ATTEMPTS, e.getDetails());
      }
      backoff(attempt++);
    }
  }

  // 지수 백오프 계산
  private static void backoff(int attempt) {
    try {
      Thread.sleep(BASE_BACKOFF_MILLIS << (attempt - 1)); // 200, 400ms ... 지수 증가
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // 인터럽트 상태 복원 후 중단
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
