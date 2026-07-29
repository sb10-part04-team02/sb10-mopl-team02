package com.team02.mopl.domain.auth.jwt.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtProperties;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class JwtLogoutHandlerTest {

  private final JwtProperties jwtProperties =
      new JwtProperties(
          "this-is-a-dummy-secret-key-for-testing-purposes-only-32bytes",
          Duration.ofMinutes(10),
          Duration.ofDays(7));
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtRegistry jwtRegistry;
  private JwtUtils jwtUtils;
  private JwtLogoutHandler jwtLogoutHandler;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private Authentication mockAuth;

  @BeforeEach
  void SetUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    mockAuth = mock(Authentication.class);

    jwtUtils = spy(new JwtUtils(jwtProperties, jwtTokenProvider));
    jwtLogoutHandler = new JwtLogoutHandler(jwtUtils, jwtTokenProvider, jwtRegistry);
  }

  @Test
  @DisplayName("쿠키, 액세스 헤더가 없는경우 조기 종료한다")
  void shouldEarlyReturn_whenRefreshCookieAndAccessHeaderIsEmpty() {
    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtTokenProvider).should(never()).verifyAccessToken(anyString());
  }

  @Test
  @DisplayName("refresh 토큰 쿠키가 없는 경우 액세스토큰 제거 후 조기 종료한다")
  void shouldEarlyReturn_whenRefreshCookieDoesNotExist() {
    // given
    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtUtils.resolveAccessToken(isNull())).willReturn("access token");
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);

    String accessTokenId = UUID.randomUUID().toString();
    given(mockClaimSet.getJWTID()).willReturn(accessTokenId);

    Instant futureTime = Instant.now().plus(Duration.ofMinutes(10));
    given(mockClaimSet.getExpirationTime()).willReturn(Date.from(futureTime));

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry).should(times(1)).registerBlacklist(eq(accessTokenId), any(Duration.class));
  }

  @Test
  @DisplayName("만료된 액세스 토큰이면 블랙리스트 등록을 스킵한다")
  void shouldSkipBlacklist_whenAccessTokenIsExpired() {
    // given
    given(jwtUtils.resolveAccessToken(isNull())).willReturn("expired access token");
    given(jwtTokenProvider.verifyAccessToken(anyString()))
        .willThrow(CredentialsExpiredException.class);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry).should(never()).registerBlacklist(anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("변조된 액세스 토큰이면 블랙리스트 등록을 스킵한다")
  void shouldSkipBlacklist_whenAccessTokenIsManipulated() {
    // given
    given(jwtUtils.resolveAccessToken(isNull())).willReturn("manipulated access token");
    given(jwtTokenProvider.verifyAccessToken(anyString())).willThrow(BadCredentialsException.class);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry).should(never()).registerBlacklist(anyString(), any(Duration.class));
  }

  @Test
  @DisplayName("refreshToken에서 검증없이 userId를 추출한다")
  void shouldExtractUserIdWithoutVerification_whenRefreshTokenIsUntrusted() {
    // given
    String refreshToken = "untrusted refresh Token";
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
    request.setCookies(refreshTokenCookie);

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.parseClaimsWithoutVerification(refreshToken)).willReturn(mockClaimSet);
    UUID userId = UUID.randomUUID();
    willReturn(userId).given(jwtUtils).getUserId(mockClaimSet);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry).should(times(1)).deleteRefreshToken(eq(userId), eq(refreshToken));
  }

  @Test
  @DisplayName("유저ID를 추출할 수 없으면 refreshToken을 삭제하지 않는다")
  void shouldNotDeleteRefreshToken_whenUserIdExtractionFails() {
    // given
    String refreshToken = "untrusted refresh Token";
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
    request.setCookies(refreshTokenCookie);

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.parseClaimsWithoutVerification(refreshToken)).willReturn(mockClaimSet);
    willThrow(BadCredentialsException.class).given(jwtUtils).getUserId(mockClaimSet);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry).should(never()).deleteRefreshToken(any(), anyString());
  }

  @Test
  @DisplayName("유효한 토큰들이 들어오면 refresh는 삭제하고 access는 블랙리스트에 추가한다")
  void shouldDeleteRefreshAndRegisterAccess_whenTokensAreValid() {
    // given
    String refreshToken = "Refresh Token";
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
    request.setCookies(refreshTokenCookie);

    String accessToken = "Access Token";
    request.addHeader("Authorization", "Bearer " + accessToken);

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.parseClaimsWithoutVerification(refreshToken)).willReturn(mockClaimSet);

    UUID userId = UUID.randomUUID();
    willReturn(userId).given(jwtUtils).getUserId(mockClaimSet);

    String accessTokenId = UUID.randomUUID().toString();
    given(jwtTokenProvider.verifyAccessToken(eq(accessToken))).willReturn(mockClaimSet);
    given(mockClaimSet.getJWTID()).willReturn(accessTokenId);

    Instant futureTime = Instant.now().plus(Duration.ofMinutes(10));
    given(mockClaimSet.getExpirationTime()).willReturn(Date.from(futureTime));

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry).should(times(1)).deleteRefreshToken(eq(userId), eq(refreshToken));
    then(jwtRegistry).should(times(1)).registerBlacklist(eq(accessTokenId), any(Duration.class));
  }
}
