package com.team02.mopl.domain.content.ingestion.sportsdb;

import com.team02.mopl.domain.content.ingestion.exception.SportsDbApiException;
import com.team02.mopl.domain.content.ingestion.sportsdb.dto.SportsDbEventDto;
import com.team02.mopl.domain.content.ingestion.sportsdb.dto.SportsDbEventsResponse;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// SportsDB HTTP 호출 캡슐화. 에러 응답은 RestClient 상태 핸들러가, IO 오류는 이 클래스가 SportsDbApiException으로 변환
@Slf4j
@Component
public class SportsDbClient {

  private static final int MAX_ATTEMPTS = 3; // 최초 호출 포함 총 시도 횟수
  private static final long BASE_BACKOFF_MILLIS = 200L; // 지수 백오프 기준: 200, 400ms ...

  private final RestClient restClient;
  private final String apiKey; // IO 예외 메시지의 URI 마스킹용

  public SportsDbClient(
      @Qualifier("sportsDbRestClient") RestClient restClient, SportsDbProperties properties) {
    this.restClient = restClient;
    this.apiKey = properties.apiKey();
  }

  // 리그+시즌의 경기 목록 조회. 무료 키는 호출당 최대 15건 반환 (유료 키 - 3000건)
  // 결과가 없으면 SportsDB가 {"events": null}을 반환하므로 빈 리스트가 된다 (DTO에서 방어)
  // Ex: https://www.thesportsdb.com/api/v1/json/123/eventsseason.php?id=4328&s=2014-2015
  public List<SportsDbEventDto> fetchSeasonEvents(String leagueId, String season) {
    SportsDbEventsResponse body =
        fetch(
            () ->
                requireBody(
                    restClient
                        .get()
                        .uri(
                            uriBuilder ->
                                uriBuilder
                                    .path("/eventsseason.php")
                                    .queryParam("id", leagueId)
                                    .queryParam("s", season)
                                    .build())
                        .retrieve()
                        .body(SportsDbEventsResponse.class)));
    return body.events();
  }

  // 호출 공통 재시도. 일시적 장애(5xx, 429, IO/타임아웃)만 지수 백오프로 재시도하고
  // 4xx 등 비일시적 오류는 즉시 던져 불필요한 재시도를 피한다. IO 오류는 SportsDbApiException으로 래핑.
  private <T> T fetch(Supplier<T> operation) {
    int attempt = 1;
    while (true) {
      try {
        return operation.get(); // 성공하면 즉시 반환
      } catch (RestClientException e) { // IO/타임아웃 등 상태 핸들러가 잡지 못한 오류
        if (attempt >= MAX_ATTEMPTS) {
          throw new SportsDbApiException(sanitize(e));
        }
        // 재시도 전 기록. 예외 메시지에는 API 키가 포함된 URI가 담길 수 있어 클래스명만 남긴다
        log.warn(
            "SportsDB 호출 실패로 재시도합니다. attempt={}/{}, cause={}",
            attempt,
            MAX_ATTEMPTS,
            e.getClass().getSimpleName());
      } catch (SportsDbApiException e) { // 상태 핸들러가 던진 4xx/5xx
        if (!e.isRetryable() || attempt >= MAX_ATTEMPTS) {
          throw e;
        }
        // details는 statusCode만 담겨 키가 새지 않으므로 그대로 남긴다
        log.warn(
            "SportsDB 호출 실패로 재시도합니다. attempt={}/{}, details={}",
            attempt,
            MAX_ATTEMPTS,
            e.getDetails());
      }
      backoff(attempt++);
    }
  }

  // IO 예외 메시지에는 API 키가 포함된 요청 URI가 담긴다
  // 로그 스택트레이스로 키가 새지 않도록 메시지만 마스킹하고, 원본 타입명/스택/cause 체인은 보존한다
  private RestClientException sanitize(RestClientException e) {
    RestClientException masked =
        new RestClientException(
            e.getClass().getSimpleName()
                + ": "
                + SportsDbClientConfig.maskApiKey(String.valueOf(e.getMessage()), apiKey),
            e.getCause());
    masked.setStackTrace(e.getStackTrace());
    return masked;
  }

  // 지수 백오프 계산
  private static void backoff(int attempt) {
    try {
      Thread.sleep(BASE_BACKOFF_MILLIS << (attempt - 1)); // 200, 400ms ... 지수 증가
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // 인터럽트 상태 복원 후 중단
      throw new SportsDbApiException(e);
    }
  }

  private static <T> T requireBody(T body) {
    if (body == null) {
      throw new SportsDbApiException(new IllegalStateException("SportsDB 응답 본문이 비어 있습니다."));
    }
    return body;
  }
}
