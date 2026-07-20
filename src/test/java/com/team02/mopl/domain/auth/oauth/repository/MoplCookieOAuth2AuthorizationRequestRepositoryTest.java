package com.team02.mopl.domain.auth.oauth.repository;

import static com.team02.mopl.domain.auth.oauth.repository.MoplCookieOAuth2AuthorizationRequestRepository.COOKIE_EXPIRE_SECONDS;
import static com.team02.mopl.domain.auth.oauth.repository.MoplCookieOAuth2AuthorizationRequestRepository.OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class MoplCookieOAuth2AuthorizationRequestRepositoryTest {

  private MoplCookieOAuth2AuthorizationRequestRepository requestRepository;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private String authorizationUri;
  private String redirectUri;
  private String clientId;
  private String state;

  @BeforeEach
  void setUp() {
    requestRepository = new MoplCookieOAuth2AuthorizationRequestRepository();

    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    authorizationUri = "https://accounts.google.com/o/oauth2/v2/auth";
    redirectUri = "http://localhost:8080/login/oauth2/code/google";
    clientId = "test-client-id";
    state = "test-stat";
  }

  private OAuth2AuthorizationRequest createTestAuthorizationRequest() {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri(authorizationUri)
        .redirectUri(redirectUri)
        .clientId(clientId)
        .state(state)
        .build();
  }

  @Nested
  class LoadAuthorizationRequest {
    @Test
    @DisplayName("request에 유효한 쿠키가 있을경우 객체 복원에 성공한다")
    void success_shouldRestoreOAuth2AuthorizationRequestSuccessfully_whenRequestCookieIsValid() {
      // given
      OAuth2AuthorizationRequest oauth2Request = createTestAuthorizationRequest();
      requestRepository.saveAuthorizationRequest(oauth2Request, request, response);
      Cookie savedCookie = response.getCookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
      request.setCookies(savedCookie);

      // when
      OAuth2AuthorizationRequest loadedRequest =
          requestRepository.loadAuthorizationRequest(request);

      // then
      assertThat(loadedRequest).isNotNull();
      assertThat(loadedRequest.getAuthorizationUri()).isEqualTo(authorizationUri);
      assertThat(loadedRequest.getRedirectUri()).isEqualTo(redirectUri);
      assertThat(loadedRequest.getClientId()).isEqualTo(clientId);
      assertThat(loadedRequest.getState()).isEqualTo(state);
    }

    @Test
    @DisplayName("request에 쿠키가 없는경우 null을 반환한다")
    void fail_shouldReturnNull_whenRequestCookieIsNull() {
      // when
      OAuth2AuthorizationRequest loadedRequest =
          requestRepository.loadAuthorizationRequest(request);

      // then
      assertThat(loadedRequest).isNull();
    }
  }

  @Nested
  class SaveAuthorizationRequest {
    @Test
    @DisplayName("authorizationRequest가 주어지면 직렬화된 쿠키가 Response에 등록된다")
    void success_shouldRegisterSerializedCookie_whenAuthorizationRequestIsProvided() {
      // given
      OAuth2AuthorizationRequest authRequest = createTestAuthorizationRequest();

      // when
      requestRepository.saveAuthorizationRequest(authRequest, request, response);

      // then
      Cookie cookie = response.getCookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

      assertThat(cookie).isNotNull();
      assertThat(cookie.isHttpOnly()).isTrue();
      assertThat(cookie.getSecure()).isTrue();
      assertThat(cookie.getPath()).isEqualTo("/");
      assertThat(cookie.getMaxAge()).isEqualTo(COOKIE_EXPIRE_SECONDS);
      assertThat(cookie.getValue()).isNotEmpty();
    }

    @Test
    @DisplayName("authorizationRequest가 null이면 기존 쿠키를 삭제하는 쿠키가 등록된다")
    void success_shouldRegisterDeleteCookie_whenAuthorizationRequestIsNull() {
      // given
      OAuth2AuthorizationRequest oauth2Request = createTestAuthorizationRequest();
      requestRepository.saveAuthorizationRequest(oauth2Request, request, response);
      Cookie savedCookie = response.getCookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
      request.setCookies(savedCookie);

      // when
      requestRepository.saveAuthorizationRequest(null, request, response);

      // then
      Cookie cookie = response.getCookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
      assertThat(cookie).isNotNull();
      assertThat(cookie.getValue()).isEmpty();
      assertThat(cookie.getMaxAge()).isZero();
    }
  }

  @Nested
  class RemoveAuthorizationRequest {
    @Test
    @DisplayName("기존 쿠키를 읽어서 반환하고 Response는 쿠키삭제설정한다")
    void success_shouldReturnOAuth2RequestAndRegisterDeleteCookie_whenRequestIsProvided() {
      // given
      OAuth2AuthorizationRequest authRequest = createTestAuthorizationRequest();
      requestRepository.saveAuthorizationRequest(authRequest, request, response);
      Cookie savedCookie = response.getCookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
      request.setCookies(savedCookie);

      // when
      OAuth2AuthorizationRequest removedRequest =
          requestRepository.removeAuthorizationRequest(request, response);

      // then
      assertThat(removedRequest).isNotNull();
      assertThat(removedRequest.getState()).isEqualTo(state);

      Cookie deletedCookie = response.getCookie(OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
      assertThat(deletedCookie).isNotNull();
      assertThat(deletedCookie.getValue()).isEmpty();
      assertThat(deletedCookie.getMaxAge()).isZero();
    }
  }
}
