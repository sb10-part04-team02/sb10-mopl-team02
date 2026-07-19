package com.team02.mopl.global.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DiscordNotifierTest {

  // 웹훅 URL의 마지막 경로 세그먼트(토큰)가 곧 인증 수단이라 로그에 남으면 안 된다
  private static final String TOKEN = "s3cr3t-webhook-token";
  private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123/" + TOKEN;

  private MockRestServiceServer server;
  private DiscordNotifier discordNotifier;
  private ListAppender<ILoggingEvent> logAppender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    DiscordProperties properties =
        new DiscordProperties(WEBHOOK_URL, Duration.ofSeconds(3), Duration.ofSeconds(5));
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    discordNotifier = new DiscordNotifier(builder.build(), properties);

    logAppender = new ListAppender<>();
    logAppender.start();
    logger = (Logger) LoggerFactory.getLogger(DiscordNotifier.class);
    logger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logAppender);
  }

  @Test
  @DisplayName("전송이 오류 응답으로 실패해도 예외를 전파하지 않고, 로그에 웹훅 토큰 대신 상태 코드만 남긴다")
  void notify_whenServerError_swallowsAndLogsStatusWithoutToken() {
    // given - 디스코드가 5xx로 응답
    server.expect(anything()).andRespond(withServerError());

    // when & then - 알림 실패가 호출측(배치)을 깨뜨리지 않는다
    assertThatCode(() -> discordNotifier.notify("배치 실패")).doesNotThrowAnyException();

    // 예외 메시지에는 요청 URL(=토큰)이 담기므로 통째로 로깅하면 안 된다
    assertThat(loggedText()).contains("status=500").doesNotContain(TOKEN);
  }

  @Test
  @DisplayName("전송이 I/O 오류로 실패해도 예외를 전파하지 않고, 로그에 웹훅 토큰 대신 예외 유형만 남긴다")
  void notify_whenIoError_swallowsAndLogsCauseWithoutToken() {
    // given - 연결/읽기 타임아웃 (RestClient가 요청 URL을 메시지에 담아 ResourceAccessException으로 감싼다)
    server
        .expect(anything())
        .andRespond(withException(new SocketTimeoutException("Connect timed out")));

    // when & then
    assertThatCode(() -> discordNotifier.notify("배치 실패")).doesNotThrowAnyException();

    assertThat(loggedText()).contains("cause=ResourceAccessException").doesNotContain(TOKEN);
  }

  // 포맷 인자까지 펼친 최종 로그 문자열 + 스택트레이스(있다면)를 합쳐 검사한다
  private String loggedText() {
    StringBuilder text = new StringBuilder();
    for (ILoggingEvent event : logAppender.list) {
      text.append(event.getFormattedMessage());
      if (event.getThrowableProxy() != null) {
        text.append(event.getThrowableProxy().getMessage());
      }
    }
    return text.toString();
  }
}
