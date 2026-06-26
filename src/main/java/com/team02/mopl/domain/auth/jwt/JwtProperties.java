package com.team02.mopl.domain.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mopl.jwt")
public record JwtProperties(
    String secretKey, Duration accessTokenExpiration, Duration refreshTokenExpiration) {

  public JwtProperties {
    if (secretKey == null || secretKey.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("JWT Secret Key는 최소 32바이트 이상이어야 합니다.");
    }

    if (accessTokenExpiration == null
        || accessTokenExpiration.isNegative()
        || accessTokenExpiration.isZero()) {
      throw new IllegalStateException("Access Token Expiration은 필수고 0보다 커야 합니다.");
    }

    if (refreshTokenExpiration == null
        || refreshTokenExpiration.isNegative()
        || refreshTokenExpiration.isZero()) {
      throw new IllegalStateException("Refresh Token Expiration은 필수고 0보다 커야 합니다.");
    }
  }
}
