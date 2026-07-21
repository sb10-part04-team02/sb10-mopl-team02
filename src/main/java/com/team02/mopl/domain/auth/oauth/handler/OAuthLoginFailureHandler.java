package com.team02.mopl.domain.auth.oauth.handler;

import static com.team02.mopl.domain.auth.oauth.handler.OAuthLoginSuccessHandler.extractBaseUrl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class OAuthLoginFailureHandler implements AuthenticationFailureHandler {

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {

    // baseUrl 추출
    String baseUrl = extractBaseUrl(request);

    // 에러메세지용 URL 제작
    String errorUrl = generateErrorUrl(baseUrl, exception.getMessage());

    response.sendRedirect(errorUrl);
  }

  public static String generateErrorUrl(String baseUrl, String errorMessage) {
    String message = (errorMessage != null) ? errorMessage : "unknown_error";

    // 예시
    // http://localhost:8080/#/sign-in?error=oauth_failed&error_message=user_not_exists
    String fragmentWithParams =
        UriComponentsBuilder.fromPath("/sign-in")
            .queryParam("error", "oauth_failed")
            .queryParam("error_message", URLEncoder.encode(message, StandardCharsets.UTF_8))
            .build()
            .toString();

    return UriComponentsBuilder.fromUriString(baseUrl)
        .fragment(fragmentWithParams)
        .build()
        .toString();
  }
}
