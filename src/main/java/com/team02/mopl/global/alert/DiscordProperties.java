package com.team02.mopl.global.alert;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 디스코드 웹훅 알림 설정
// - webhookUrl은 미설정 환경(로컬/테스트)의 부팅을 막지 않도록 blank를 허용. blank면 알림 비활성
@ConfigurationProperties(prefix = "app.alert.discord")
public record DiscordProperties(String webhookUrl, Duration connectTimeout, Duration readTimeout) {

  public DiscordProperties {
    if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
      throw new IllegalStateException("디스코드 알림 connect-timeout은 필수고 0보다 커야 합니다.");
    }
    if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
      throw new IllegalStateException("디스코드 알림 read-timeout은 필수고 0보다 커야 합니다.");
    }
  }

  public boolean enabled() {
    return webhookUrl != null && !webhookUrl.isBlank();
  }
}
