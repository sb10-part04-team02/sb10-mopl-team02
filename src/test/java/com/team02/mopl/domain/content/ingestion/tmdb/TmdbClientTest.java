package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbPageResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TmdbClientTest {

  private static final String BASE_URL = "https://api.themoviedb.org/3";

  private MockRestServiceServer server;
  private TmdbClient tmdbClient;

  @BeforeEach
  void setUp() {
    TmdbProperties properties =
        new TmdbProperties(
            "test-token",
            BASE_URL,
            "https://image.tmdb.org/t/p/w500",
            "ko-KR",
            2,
            Duration.ofSeconds(3),
            Duration.ofSeconds(10));
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    tmdbClient =
        new TmdbClient(TmdbClientConfig.customize(builder, properties).build(), properties);
  }

  @Test
  @DisplayName("fetchPopularMovies는 Bearer 토큰과 language/page 파라미터로 호출하고 응답을 DTO로 역직렬화한다")
  void fetchPopularMovies_deserializesResponse() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/movie/popular")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", "Bearer test-token"))
        .andExpect(queryParam("language", "ko-KR"))
        .andExpect(queryParam("page", "1"))
        .andRespond(
            withSuccess(
                """
                {
                  "page": 1,
                  "results": [
                    {
                      "id": 550,
                      "title": "파이트 클럽",
                      "overview": "줄거리",
                      "poster_path": "/poster.jpg",
                      "genre_ids": [18, 53],
                      "unknown_field": "ignored"
                    }
                  ],
                  "total_pages": 100
                }
                """,
                MediaType.APPLICATION_JSON));

    TmdbPageResponse<TmdbMovieDto> response = tmdbClient.fetchPopularMovies(1);

    assertThat(response.page()).isEqualTo(1);
    assertThat(response.totalPages()).isEqualTo(100);
    TmdbMovieDto movie = response.results().get(0);
    assertThat(movie.id()).isEqualTo(550L);
    assertThat(movie.title()).isEqualTo("파이트 클럽");
    assertThat(movie.overview()).isEqualTo("줄거리");
    assertThat(movie.posterPath()).isEqualTo("/poster.jpg");
    assertThat(movie.genreIds()).containsExactly(18, 53);
  }

  @Test
  @DisplayName("fetchPopularTv는 name 필드를 가진 드라마 응답을 DTO로 역직렬화한다")
  void fetchPopularTv_deserializesResponse() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/tv/popular")))
        .andExpect(queryParam("page", "2"))
        .andRespond(
            withSuccess(
                """
                {
                  "page": 2,
                  "results": [
                    {"id": 1399, "name": "왕좌의 게임", "overview": "줄거리", "poster_path": null, "genre_ids": [10765]}
                  ],
                  "total_pages": 50
                }
                """,
                MediaType.APPLICATION_JSON));

    TmdbPageResponse<TmdbTvDto> response = tmdbClient.fetchPopularTv(2);

    TmdbTvDto tv = response.results().get(0);
    assertThat(tv.id()).isEqualTo(1399L);
    assertThat(tv.name()).isEqualTo("왕좌의 게임");
    assertThat(tv.posterPath()).isNull();
    assertThat(tv.genreIds()).containsExactly(10765);
  }

  @Test
  @DisplayName("fetchMovieGenres는 장르 목록을 id-이름 Map으로 변환한다")
  void fetchMovieGenres_returnsIdToNameMap() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/genre/movie/list")))
        .andExpect(queryParam("language", "ko-KR"))
        .andRespond(
            withSuccess(
                """
                {"genres": [{"id": 28, "name": "액션"}, {"id": 35, "name": "코미디"}]}
                """,
                MediaType.APPLICATION_JSON));

    Map<Integer, String> genres = tmdbClient.fetchMovieGenres();

    assertThat(genres).containsExactlyInAnyOrderEntriesOf(Map.of(28, "액션", 35, "코미디"));
  }

  @Test
  @DisplayName("401 응답이면 상태 코드를 details에 담은 TmdbApiException을 던진다")
  void fetchPopularMovies_whenUnauthorized_throwsTmdbApiException() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/movie/popular")))
        .andRespond(withUnauthorizedRequest());

    assertThatThrownBy(() -> tmdbClient.fetchPopularMovies(1))
        .isInstanceOf(TmdbApiException.class)
        .satisfies(
            e ->
                assertThat(((TmdbApiException) e).getDetails()).containsEntry("statusCode", "401"));
  }

  @Test
  @DisplayName("5xx 응답이면 TmdbApiException을 던진다")
  void fetchTvGenres_whenServerError_throwsTmdbApiException() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/genre/tv/list")))
        .andRespond(withServerError());

    assertThatThrownBy(() -> tmdbClient.fetchTvGenres()).isInstanceOf(TmdbApiException.class);
  }

  @Test
  @DisplayName("타임아웃 등 IO 오류는 TmdbApiException으로 래핑한다")
  void fetchPopularMovies_whenIoError_wrapsInTmdbApiException() {
    server
        .expect(requestTo(Matchers.startsWith(BASE_URL + "/movie/popular")))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    assertThatThrownBy(() -> tmdbClient.fetchPopularMovies(1))
        .isInstanceOf(TmdbApiException.class)
        .hasRootCauseInstanceOf(SocketTimeoutException.class);
  }
}
