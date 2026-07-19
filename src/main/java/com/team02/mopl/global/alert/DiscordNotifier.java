package com.team02.mopl.global.alert;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 운영 알림용 디스코드 웹훅 전송기
// - webhook-url 미설정(빈 값)이면 아무것도 하지 않는다 (로컬/테스트 기본 비활성)
// - 알림 실패가 호출측(배치 등)을 깨뜨리지 않도록 모든 예외를 삼키고 warn 로그만 남긴다
@Slf4j
@Component
public class DiscordNotifier {

  static final int MAX_CONTENT_LENGTH = 2000; // 디스코드 메시지 본문 최대 길이

  private final RestClient restClient;
  private final DiscordProperties properties;

  public DiscordNotifier(
      @Qualifier("discordRestClient") RestClient restClient, DiscordProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public void notify(String message) {
    if (!properties.enabled()) {
      log.debug("디스코드 웹훅 URL이 설정되지 않아 알림을 건너뜁니다.");
      return;
    }
    try {
      restClient
          .post()
          .uri(properties.webhookUrl())
          .contentType(MediaType.APPLICATION_JSON)
          .body(Map.of("content", truncate(message)))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      // 예외를 통째로 로깅하지 않고 원인 분석에 필요한 수준으로만 축약한다 (웹훅 URL 노출 방지)
      log.warn("디스코드 알림 전송에 실패했습니다. status={}", e.getStatusCode().value());
    } catch (Exception e) {
      log.warn("디스코드 알림 전송에 실패했습니다. cause={}", e.getClass().getSimpleName());
    }
  }

  private static String truncate(String message) {
    if (message.length() <= MAX_CONTENT_LENGTH) {
      return message;
    }
    return message.substring(0, MAX_CONTENT_LENGTH - 3) + "...";
  }
}
