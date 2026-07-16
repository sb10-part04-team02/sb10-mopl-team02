package com.team02.mopl.domain.auth.oauth.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MoplOidcProviderRegistryTest {

  @Test
  @DisplayName("지원하는 registrationId가 들어오면 알맞은 Provider를 반환한다")
  void success_shouldReturnSuitableProvider_whenRegistrationMatches() {
    // given
    MoplOidcProvider googleProvider = mock(MoplOidcProvider.class);
    MoplOidcProvider kakaoProvider = mock(MoplOidcProvider.class);

    given(googleProvider.supports()).willReturn(OAuthType.GOOGLE);
    given(kakaoProvider.supports()).willReturn(OAuthType.KAKAO);

    MoplOidcProviderRegistry registry =
        new MoplOidcProviderRegistry(List.of(googleProvider, kakaoProvider));

    // when
    MoplOidcProvider actual = registry.get("google");

    // then
    assertThat(actual).isEqualTo(googleProvider);
  }

  private static Stream<String> provideRegistrationId() {
    return Stream.of("naver", null);
  }

  @ParameterizedTest
  @MethodSource("provideRegistrationId")
  @DisplayName("지원하지 않는 registrationId가 들어오면 예외를 던진다")
  void fail_shouldThrowException_whenRegistrationIsNotSupported(String registrationId) {
    // given
    MoplOidcProvider googleProvider = mock(MoplOidcProvider.class);
    given(googleProvider.supports()).willReturn(OAuthType.GOOGLE);

    MoplOidcProviderRegistry registry = new MoplOidcProviderRegistry(List.of(googleProvider));

    // when & then
    assertThrows(IllegalArgumentException.class, () -> registry.get(registrationId));
  }

  @Test
  @DisplayName("oAuthtype은 준비됐지만 그에 맞는 provider가 등록되지 않았으면 예외를 던진다")
  void fail_shouldThrowException_whenProviderBeanIsMissing() {
    // given
    MoplOidcProvider kakaoProvider = mock(MoplOidcProvider.class);
    given(kakaoProvider.supports()).willReturn(OAuthType.KAKAO);

    MoplOidcProviderRegistry registry = new MoplOidcProviderRegistry(List.of(kakaoProvider));

    // when & then
    assertThrows(IllegalArgumentException.class, () -> registry.get("google"));
  }
}
