package com.team02.mopl.domain.auth.oauth.repository;

import com.team02.mopl.global.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

@Component
public class MoplCookieOAuth2AuthorizationRequestRepository
    implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

  public static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
  public static final int COOKIE_EXPIRE_SECONDS = 180; // 3분

  // 소셜 로그인 후 백엔드 콜백이 왔을때
  @Override
  public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
    return CookieUtils.getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME)
        .map(cookie -> CookieUtils.deserialize(cookie, OAuth2AuthorizationRequest.class))
        .orElse(null);
  }

  // 로그인 시작할 때
  @Override
  public void saveAuthorizationRequest(
      OAuth2AuthorizationRequest authorizationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    // 인증 끝 or 로그인이 실패했을 경우 임시 토큰 삭제
    if (authorizationRequest == null) {
      CookieUtils.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
      return;
    }

    // OAuth2AuthorizationRequest 객체를 직렬화해서 쿠키에 저장
    CookieUtils.addCookie(
        response,
        OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME,
        CookieUtils.serialize(authorizationRequest),
        COOKIE_EXPIRE_SECONDS);
  }

  // 인증이 성공했거나 실패했을때
  @Override
  public OAuth2AuthorizationRequest removeAuthorizationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    OAuth2AuthorizationRequest authorizationRequest = this.loadAuthorizationRequest(request);
    CookieUtils.deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);

    return authorizationRequest;
  }
}
