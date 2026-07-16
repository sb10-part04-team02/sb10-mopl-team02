package com.team02.mopl.domain.auth.oauth.provider;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.Getter;

public enum OAuthType {
  GOOGLE("google"),
  KAKAO("kakao");

  @Getter private final String registrationId;
  private static final Map<String, OAuthType> AUTH_TYPE_MAP =
      Collections.unmodifiableMap(
          Arrays.stream(values()).collect(toMap(OAuthType::getRegistrationId, identity())));

  OAuthType(String registrationId) {
    this.registrationId = registrationId;
  }

  public static OAuthType of(String registrationId) {
    if (registrationId == null) {
      throw new IllegalArgumentException("registration은 null일 수 없습니다");
    }

    return Optional.ofNullable(AUTH_TYPE_MAP.get(registrationId))
        .orElseThrow(
            () -> new IllegalArgumentException("지원하지 않는 소셜 로그인 타입입니다. type=" + registrationId));
  }
}
