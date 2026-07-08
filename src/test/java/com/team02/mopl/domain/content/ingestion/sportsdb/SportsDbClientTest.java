package com.team02.mopl.domain.content.ingestion.sportsdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.team02.mopl.domain.content.ingestion.exception.SportsDbApiException;
import com.team02.mopl.domain.content.ingestion.sportsdb.SportsDbProperties.League;
import com.team02.mopl.domain.content.ingestion.sportsdb.dto.SportsDbEventDto;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SportsDbClientTest {

  private static final String BASE_URL = "https://www.thesportsdb.com/api/v1/json";
  private static final String API_KEY = "test-key";
  private static final String KEYED_URL = BASE_URL + "/" + API_KEY;

  private MockRestServiceServer server;
  private SportsDbClient sportsDbClient;

  @BeforeEach
  void setUp() {
    SportsDbProperties properties =
        new SportsDbProperties(
            API_KEY,
            BASE_URL,
            Duration.ofSeconds(3),
            Duration.ofSeconds(10),
            List.of(new League("4328", "2025-2026")));
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    sportsDbClient =
        new SportsDbClient(SportsDbClientConfig.customize(builder, properties).build());
  }

  @Test
  @DisplayName("fetchSeasonEvents는 키가 포함된 경로와 id/s 파라미터로 호출하고 응답을 DTO로 역직렬화한다")
  void fetchSeasonEvents_deserializesResponse() {
    // given
    server
        .expect(requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andExpect(method(HttpMethod.GET))
        .andExpect(queryParam("id", "4328"))
        .andExpect(queryParam("s", "2025-2026"))
        .andRespond(
            withSuccess(
                """
                {
                  "events": [
                    {
                      "idEvent": "2267073",
                      "strEvent": "Liverpool vs Bournemouth",
                      "strDescriptionEN": null,
                      "strSport": "Soccer",
                      "strLeague": "English Premier League",
                      "strSeason": "2025-2026",
                      "strVenue": "Anfield",
                      "dateEvent": "2025-08-15",
                      "strThumb": "https://img/thumb.jpg",
                      "strPoster": "https://img/poster.jpg",
                      "unknown_field": "ignored"
                    }
                  ]
                }
                """,
                MediaType.APPLICATION_JSON));

    // when
    List<SportsDbEventDto> events = sportsDbClient.fetchSeasonEvents("4328", "2025-2026");

    // then
    assertThat(events).hasSize(1);
    SportsDbEventDto event = events.get(0);
    assertThat(event.idEvent()).isEqualTo("2267073");
    assertThat(event.strEvent()).isEqualTo("Liverpool vs Bournemouth");
    assertThat(event.strSport()).isEqualTo("Soccer");
    assertThat(event.strLeague()).isEqualTo("English Premier League");
    assertThat(event.strSeason()).isEqualTo("2025-2026");
    assertThat(event.strVenue()).isEqualTo("Anfield");
    assertThat(event.dateEvent()).isEqualTo("2025-08-15");
    assertThat(event.strThumb()).isEqualTo("https://img/thumb.jpg");
    assertThat(event.strPoster()).isEqualTo("https://img/poster.jpg");
  }

  @Test
  @DisplayName("결과가 없어 {\"events\": null}이 오면 빈 리스트를 반환한다")
  void fetchSeasonEvents_whenEventsNull_returnsEmptyList() {
    // given: SportsDB는 결과 없음을 {"events": null}로 응답
    server
        .expect(requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andRespond(withSuccess("{\"events\": null}", MediaType.APPLICATION_JSON));

    // when
    List<SportsDbEventDto> events = sportsDbClient.fetchSeasonEvents("4328", "1999-2000");

    // then
    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName("4xx 응답은 재시도 없이 즉시 던지고, details에는 statusCode만 담고 URI(키 포함)는 담지 않는다")
  void fetchSeasonEvents_whenUnauthorized_doesNotRetryAndHidesUri() {
    // given: 4xx는 1회만 호출되어야 함
    server
        .expect(
            ExpectedCount.once(), requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andRespond(withUnauthorizedRequest());

    // when & then
    assertThatThrownBy(() -> sportsDbClient.fetchSeasonEvents("4328", "2025-2026"))
        .isInstanceOf(SportsDbApiException.class)
        .satisfies(
            e -> {
              SportsDbApiException exception = (SportsDbApiException) e;
              assertThat(exception.getDetails()).containsEntry("statusCode", "401");
              // API 키가 포함된 URI는 클라이언트 노출 정보(details)에 담지 않는다
              assertThat(exception.getDetails()).doesNotContainKey("uri");
              assertThat(exception.getDetails().toString()).doesNotContain(API_KEY);
            });
    server.verify();
  }

  @Test
  @DisplayName("5xx 응답이 계속되면 MAX_ATTEMPTS만큼 재시도한 뒤 SportsDbApiException을 던진다")
  void fetchSeasonEvents_whenServerError_retriesThenThrowsSportsDbApiException() {
    // given: 3회 모두 5xx
    server
        .expect(
            ExpectedCount.times(3), requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andRespond(withServerError());

    // when & then
    assertThatThrownBy(() -> sportsDbClient.fetchSeasonEvents("4328", "2025-2026"))
        .isInstanceOf(SportsDbApiException.class);
    server.verify();
  }

  @Test
  @DisplayName("429(rate limit) 이후 정상 응답이 오면 재시도로 복구해 결과를 반환한다")
  void fetchSeasonEvents_whenRateLimited_retriesAndSucceeds() {
    // given: 첫 호출은 429, 재시도 호출은 성공
    server
        .expect(requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
    server
        .expect(requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andRespond(
            withSuccess(
                """
                {"events": [{"idEvent": "2267073", "strEvent": "Liverpool vs Bournemouth"}]}
                """,
                MediaType.APPLICATION_JSON));

    // when
    List<SportsDbEventDto> events = sportsDbClient.fetchSeasonEvents("4328", "2025-2026");

    // then
    assertThat(events).hasSize(1);
    server.verify();
  }

  @Test
  @DisplayName("200 응답이지만 본문이 비어 있으면 requireBody가 재시도 후 SportsDbApiException을 던진다")
  void fetchSeasonEvents_whenBodyIsNull_retriesThenThrowsSportsDbApiException() {
    // given: 200이지만 본문이 없어 역직렬화 결과가 null (null 본문은 retryable이라 MAX_ATTEMPTS만큼 호출됨)
    server
        .expect(
            ExpectedCount.times(3), requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andRespond(withSuccess());

    // when & then
    assertThatThrownBy(() -> sportsDbClient.fetchSeasonEvents("4328", "2025-2026"))
        .isInstanceOf(SportsDbApiException.class);
    server.verify();
  }

  @Test
  @DisplayName("타임아웃 등 IO 오류는 재시도 후 SportsDbApiException으로 래핑한다")
  void fetchSeasonEvents_whenIoError_retriesThenWrapsInSportsDbApiException() {
    // given: 3회 모두 IO 오류
    server
        .expect(
            ExpectedCount.times(3), requestTo(Matchers.startsWith(KEYED_URL + "/eventsseason.php")))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    // when & then
    assertThatThrownBy(() -> sportsDbClient.fetchSeasonEvents("4328", "2025-2026"))
        .isInstanceOf(SportsDbApiException.class)
        .hasRootCauseInstanceOf(SocketTimeoutException.class);
    server.verify();
  }

  @Test
  @DisplayName("maskApiKey는 URI의 키 경로 세그먼트를 ***로 가린다")
  void maskApiKey_masksKeyPathSegment() {
    // given
    String uri = BASE_URL + "/" + API_KEY + "/eventsseason.php?id=4328&s=2025-2026";

    // when
    String masked = SportsDbClientConfig.maskApiKey(uri, API_KEY);

    // then
    assertThat(masked).isEqualTo(BASE_URL + "/***/eventsseason.php?id=4328&s=2025-2026");
    assertThat(masked).doesNotContain(API_KEY);
  }
}
