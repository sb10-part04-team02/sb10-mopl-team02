package com.team02.mopl.domain.auth.oauth.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;

@ExtendWith(MockitoExtension.class)
class OAuthLoginFailureHandlerTest {

  @InjectMocks private OAuthLoginFailureHandler failureHandler;

  private static Stream<String> provideErrorMessages() {
    return Stream.of("auth_error!!", null);
  }

  @ParameterizedTest
  @MethodSource("provideErrorMessages")
  @DisplayName("인증에 실패하면 url을 redirect한다")
  void success_shouldRedirectUrl_whenAuthenticationFails(String errorMessage) throws IOException {
    // given
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationException mockAuthEx = mock(AuthenticationException.class);

    request.setScheme("https");
    request.setServerName("localhost");
    request.setServerPort(8080);
    request.setRequestURI("/login/oauth2/code/*"); // *는 registrationId

    String encodeMessage =
        errorMessage != null
            ? URLEncoder.encode(errorMessage, StandardCharsets.UTF_8)
            : "unknown_error";

    given(mockAuthEx.getMessage()).willReturn(errorMessage);

    // when
    failureHandler.onAuthenticationFailure(request, response, mockAuthEx);

    // then
    assertThat(response.getRedirectedUrl())
        .isEqualTo(
            "https://localhost:8080/#/sign-in?error=oauth_failed&error_message=" + encodeMessage);
  }
}
