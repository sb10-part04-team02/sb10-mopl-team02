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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Slf4j
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

    // 둘 다 없는 경우
    if (refreshToken == null && accessToken == null) {
      return;
    }

    // refreshToken은 오지 않는 경우(쿠키 강제 삭제, 서드파티 쿠키 제한)
    if (refreshToken == null) {
      invalidateAccessToken(accessToken);
      return;
    }

    // accessToken 오지 않는 경우(토큰 만료로 브라우저에서 제거, 클라이언트 버그)
    UUID userId = extractUserIdWithoutVerification(refreshToken);
    if (userId != null) {
      jwtRegistry.deleteRefreshToken(userId, refreshToken);
    }

    // 둘 다 오는 경우
    if (accessToken != null) {
      invalidateAccessToken(accessToken);
    }
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

  private UUID extractUserIdWithoutVerification(String token) {
    try {
      JWTClaimsSet claimsSet = jwtTokenProvider.parseClaimsWithoutVerification(token);
      return jwtUtils.getUserId(claimsSet);
    } catch (Exception e) {
      return null;
    }
  }

  private void invalidateAccessToken(String accessToken) {
    try {
      JWTClaimsSet claimsSet = jwtTokenProvider.verifyAccessToken(accessToken);
      String accessTokenId = claimsSet.getJWTID();

      // 남은시간 구하기
      Instant expirationTime = claimsSet.getExpirationTime().toInstant();
      Duration remaining = Duration.between(Instant.now(), expirationTime);

      // 양수시간만 블랙리스트에 등록(음수, zero시간은 verifyAccessToken에서 걸러짐)
      jwtRegistry.registerBlacklist(accessTokenId, remaining);

    } catch (CredentialsExpiredException e) {
      // 만료된 토큰은 블랙리스트에 넣을 필요 없음
      log.debug("만료된 토큰이라 블랙리스트 등록을 생략합니다. message={}", e.getMessage());
    } catch (Exception e) {
      // 변조된 토큰은 블랙리스트에 넣을 필요 없음
      log.warn("액세스 토큰 무효화중 예외 발생!. message={}", e.getMessage(), e);
    }
  }
}
