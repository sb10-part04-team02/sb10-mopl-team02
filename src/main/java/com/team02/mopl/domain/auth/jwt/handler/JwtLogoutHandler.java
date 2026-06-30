package com.team02.mopl.domain.auth.jwt.handler;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtLogoutHandler implements LogoutHandler {

  private final JwtUtils jwtUtils;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtRegistry jwtRegistry;

  @Override
  public void logout(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication) {

    // 만료시간이 지금인 리프레시 토큰 생성 및 response에 쿠키 저장
    ResponseCookie logoutToken = jwtUtils.generateLogoutRefreshTokenCookie();
    response.addHeader(HttpHeaders.SET_COOKIE, logoutToken.toString());

    String refreshToken = resolveRefreshToken(request);
    String accessToken = jwtUtils.resolveAccessToken(request.getHeader("Authorization"));
    if (refreshToken == null || accessToken == null) {
      // 토큰이 없으면 이미 로그아웃이나 다름없음
      return;
    }

    JWTClaimsSet claimsSet;
    UUID userId;
    try {
      claimsSet = jwtTokenProvider.verifyAccessToken(accessToken);
      userId = jwtUtils.getUserId(claimsSet);
    } catch (Exception e) {
      // 변조되었으면 Redis건드릴 필요도 없음
      return;
    }
    String accessTokenId = claimsSet.getJWTID();

    // 남은시간 구하기
    Instant expirationTime = claimsSet.getExpirationTime().toInstant();
    Duration remainingDuration = Duration.between(Instant.now(), expirationTime);

    // Refresh 삭제, AccessToken 블랙리스트 등록
    jwtRegistry.deleteRefreshToken(userId, accessTokenId, remainingDuration, refreshToken);
  }

  private String resolveRefreshToken(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }

    return Arrays.stream(cookies)
        .filter(cookie -> cookie.getName().equals(JwtUtils.REFRESH_TOKEN_COOKIE_NAME))
        .map(Cookie::getValue)
        .findFirst()
        .orElse(null);
  }
}
