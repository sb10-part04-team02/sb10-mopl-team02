package com.team02.mopl.global.alert;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.net.SocketTimeoutException;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DiscordNotifierTest {

  private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123/token";

  private MockRestServiceServer server;
  private DiscordNotifier notifier;

  private void setUpNotifier() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    notifier = new DiscordNotifier(builder.build(), properties(WEBHOOK_URL));
  }

  @Test
  @DisplayName("notify는 웹훅 URL로 content JSON을 POST한다")
  void notify_postsContentJsonToWebhookUrl() {
    // given
    setUpNotifier();
    server
        .expect(requestTo(WEBHOOK_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").value("수집 실패 알림"))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    // when
    notifier.notify("수집 실패 알림");

    // then
    server.verify();
  }

  @Test
  @DisplayName("메시지가 2000자를 넘으면 잘라서 보낸다 (디스코드 본문 길이 제한)")
  void notify_truncatesMessageOverLimit() {
    // given
    setUpNotifier();
    server
        .expect(requestTo(WEBHOOK_URL))
        .andExpect(jsonPath("$.content", Matchers.hasLength(DiscordNotifier.MAX_CONTENT_LENGTH)))
        .andExpect(jsonPath("$.content", Matchers.endsWith("...")))
        .andRespond(withStatus(HttpStatus.NO_CONTENT));

    // when
    notifier.notify("가".repeat(DiscordNotifier.MAX_CONTENT_LENGTH + 500));

    // then
    server.verify();
  }

  @Test
  @DisplayName("웹훅 URL이 빈 값이면 요청을 보내지 않는다 (알림 비활성)")
  void notify_whenWebhookUrlBlank_sendsNothing() {
    // given
    RestClient restClient = mock(RestClient.class);
    DiscordNotifier disabled = new DiscordNotifier(restClient, properties(""));

    // when
    disabled.notify("메시지");

    // then
    verifyNoInteractions(restClient);
  }

  @Test
  @DisplayName("오류 응답(5xx)이어도 예외를 전파하지 않는다 (알림 실패가 배치를 깨지 않음)")
  void notify_whenServerError_doesNotPropagate() {
    // given
    setUpNotifier();
    server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());

    // when & then
    assertThatCode(() -> notifier.notify("메시지")).doesNotThrowAnyException();
    server.verify();
  }

  @Test
  @DisplayName("IO 오류(타임아웃)여도 예외를 전파하지 않는다")
  void notify_whenIoError_doesNotPropagate() {
    // given
    setUpNotifier();
    server
        .expect(requestTo(WEBHOOK_URL))
        .andRespond(withException(new SocketTimeoutException("타임아웃")));

    // when & then
    assertThatCode(() -> notifier.notify("메시지")).doesNotThrowAnyException();
  }

  private DiscordProperties properties(String webhookUrl) {
    return new DiscordProperties(webhookUrl, Duration.ofSeconds(2), Duration.ofSeconds(5));
  }
}
