package com.team02.mopl.domain.auth.jwt.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class JwtLogoutHandlerTest {

  private final JwtProperties jwtProperties =
      new JwtProperties(
          "this-is-a-dummy-secret-key-for-testing-purposes-only-32bytes",
          Duration.ofMinutes(10),
          Duration.ofDays(7));
  @Spy private JwtUtils jwtUtils = new JwtUtils(jwtProperties);
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtRegistry jwtRegistry;
  @InjectMocks private JwtLogoutHandler jwtLogoutHandler;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private Authentication mockAuth;

  @BeforeEach
  void SetUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    mockAuth = mock(Authentication.class);
  }

  @Test
  @DisplayName("쿠키 자체가 없는경우 조기 종료한다")
  void fail_shouldNotVerifyToken_whenCookieIsEmpty() {
    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtTokenProvider).should(never()).verifyAccessToken("token");
  }

  @Test
  @DisplayName("refresh 토큰 쿠키가 없는 경우 조기 종료한다")
  void fail_shouldNotVerifyToken_whenRefreshCookieDoesNotExist() {
    // given
    request.setCookies(new Cookie("another_cookie", "value"));

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtTokenProvider).should(never()).verifyAccessToken("token");
  }

  @Test
  @DisplayName("Authorization 헤더가 없으면 조기 종료한다")
  void fail_shouldNotVerifyToken_whenAuthorizationHeaderIsAbsent() {
    // given
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, "valid Token");
    request.setCookies(refreshTokenCookie);

    given(jwtUtils.resolveAccessToken(isNull())).willReturn(null);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtTokenProvider).should(never()).verifyAccessToken("token");
  }

  @Test
  @DisplayName("다른 타입의 인증토큰이 있으면 조기 종료한다")
  void fail_shouldNotVerifyToken_whenAuthorizationHeaderIsInvalid() {
    // given
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, "valid Token");
    request.setCookies(refreshTokenCookie);

    String invalidAccessToken = "Invalid Access Token";
    request.addHeader("Authorization", "Basic " + invalidAccessToken);
    given(jwtUtils.resolveAccessToken(anyString())).willReturn(null);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtTokenProvider).should(never()).verifyAccessToken("token");
  }

  @Test
  @DisplayName("access 토큰이 변조 되었으면 조기 종료한다")
  void fail_shouldNotGetUserId_whenAccessTokenIsInvalid() {
    // given
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, "valid Token");
    request.setCookies(refreshTokenCookie);

    String invalidAccessToken = "Invalid Access Token";
    request.addHeader("Authorization", "Bearer " + invalidAccessToken);

    given(jwtTokenProvider.verifyAccessToken(anyString())).willThrow(BadCredentialsException.class);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtUtils).should(never()).getUserId(any());
  }

  @Test
  @DisplayName("claimSet 내부에 userId claim이 없으면 조기 종료한다")
  void fail_shouldNotDeleteRefreshToken_whenUserIdClaimDoesNotExist() {
    // given
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, "valid Token");
    request.setCookies(refreshTokenCookie);

    String accessTokenWithoutUserId = "Access Token Without UserId";
    request.addHeader("Authorization", "Bearer " + accessTokenWithoutUserId);

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);
    doThrow(BadCredentialsException.class).when(jwtUtils).getUserId(mockClaimSet);

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry).should(never()).deleteRefreshToken(any(), any(), any(), any());
  }

  @Test
  @DisplayName("유효한 토큰들이 들어오면 성공적으로 로그아웃처리를 진행한다")
  void success_shouldCompleteLogoutSuccessfully_whenTokensAreValid() {
    // given
    String refreshToken = "Refresh Token";
    Cookie refreshTokenCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
    request.setCookies(refreshTokenCookie);

    String accessToken = "Access Token";
    request.addHeader("Authorization", "Bearer " + accessToken);

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);

    UUID userId = UUID.randomUUID();
    doReturn(userId).when(jwtUtils).getUserId(mockClaimSet);

    String accessTokenId = UUID.randomUUID().toString();
    given(mockClaimSet.getJWTID()).willReturn(accessTokenId);

    Instant futureTime = Instant.now().plus(Duration.ofMinutes(10));
    given(mockClaimSet.getExpirationTime()).willReturn(Date.from(futureTime));

    // when
    jwtLogoutHandler.logout(request, response, mockAuth);

    // then
    then(jwtRegistry)
        .should(times(1))
        .deleteRefreshToken(eq(userId), eq(accessTokenId), any(Duration.class), eq(refreshToken));
  }
}
