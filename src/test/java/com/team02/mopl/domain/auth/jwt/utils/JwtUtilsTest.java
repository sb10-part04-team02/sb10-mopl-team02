package com.team02.mopl.domain.auth.jwt.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtProperties;
import java.text.ParseException;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

  @Mock private JwtProperties jwtProperties;
  @InjectMocks private JwtUtils jwtUtils;

  @Nested
  class ResolveAccessToken {
    @Test
    @DisplayName("null이 들어올 경우 null을 반환한다")
    void fail_shouldReturnNull_whenTokenIsNull() {
      // when
      String actual = jwtUtils.resolveAccessToken(null);

      // then
      assertThat(actual).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 값이 들어올 경우 null을 반환한다")
    void fail_shouldReturnNull_whenAccessTokenIsInvalid() {
      // given
      String invalidToken = "invalid Token";

      // when
      String actual = jwtUtils.resolveAccessToken(invalidToken);

      // then
      assertThat(actual).isNull();
    }

    @Test
    @DisplayName("유효한 토큰 포맷이 들어올 경우 토큰만 반환한다")
    void success_shouldReturnToken_whenBearerFormatIsValid() {
      // given
      String accessToken = "accessToken";

      // when
      String actual = jwtUtils.resolveAccessToken("Bearer " + accessToken);

      // then
      assertThat(actual).isEqualTo(accessToken);
    }
  }

  @Test
  @DisplayName("refresh토큰이 들어오면 쿠키를 반환한다")
  void success_shouldReturnResponseCookie_whenRefreshTokenIsComing() {
    // given
    String refreshToken = "refreshToken";
    Duration expiration = Duration.ofMinutes(10);
    given(jwtProperties.refreshTokenExpiration()).willReturn(expiration);

    // when
    ResponseCookie cookie = jwtUtils.generateRefreshTokenCookie(refreshToken);

    // then
    assertThat(cookie.getValue()).isEqualTo(refreshToken);
    assertThat(cookie.getMaxAge()).isEqualTo(expiration);
    assertThat(cookie.isHttpOnly()).isEqualTo(true);
    assertThat(cookie.isSecure()).isEqualTo(true);
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }

  @Test
  @DisplayName("refresh토큰이 만료된 쿠키를 반환한다")
  void success_shouldReturnExpirationCookie_whenRequestedForLogout() {
    // when
    ResponseCookie cookie = jwtUtils.generateLogoutRefreshTokenCookie();

    // then
    assertThat(cookie.getValue()).isEqualTo("");
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    assertThat(cookie.isHttpOnly()).isEqualTo(true);
    assertThat(cookie.isSecure()).isEqualTo(true);
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }

  @Nested
  class GetUserId {
    @Test
    @DisplayName("userId의 파싱이 실패한다면 예외를 던진다")
    void fail_shouldThrowBadCredentialException_whenParseExceptionOccurs() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringClaim(anyString())).willThrow(ParseException.class);

      // when & then
      assertThrows(BadCredentialsException.class, () -> jwtUtils.getUserId(claimsSet));
    }

    @Test
    @DisplayName("userId의 파싱값이 null이라면 예외를 던진다")
    void fail_shouldThrowBadCredentialException_whenUserIdIsNull() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringClaim(anyString())).willReturn(null);

      // when & then
      assertThrows(BadCredentialsException.class, () -> jwtUtils.getUserId(claimsSet));
    }

    @Test
    @DisplayName("UUID의 파싱이 실패한다면 예외를 던진다")
    void fail_shouldThrowBadCredentialException_whenInvalidUUID() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringClaim(anyString())).willReturn("Invalid UUID");

      // when & then
      assertThrows(BadCredentialsException.class, () -> jwtUtils.getUserId(claimsSet));
    }

    @Test
    @DisplayName("userId의 파싱이 성공한다면 UUID를 반환한다")
    void success_shouldReturnUUID_whenClaimSetIsValid() throws ParseException {
      // given
      UUID userId = UUID.randomUUID();
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringClaim("userId")).willReturn(userId.toString());

      // when
      UUID actual = jwtUtils.getUserId(claimsSet);

      // then
      assertThat(actual).isEqualTo(userId);
    }
  }

  @Nested
  class GetAuthorities {
    @Test
    @DisplayName("roles의 파싱이 실패한다면 예외를 던진다")
    void fail_shouldThrowBadCredentialsException_whenParseExceptionOccurs() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString())).willThrow(ParseException.class);

      // when & then
      assertThrows(BadCredentialsException.class, () -> jwtUtils.getAuthorities(claimsSet));
    }

    @Test
    @DisplayName("roles의 파싱값이 null이면 예외를 던진다")
    void fail_shouldThrowInsufficientAuthenticationException_whenRolesIsNull()
        throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString())).willReturn(null);

      // when & then
      assertThrows(
          InsufficientAuthenticationException.class, () -> jwtUtils.getAuthorities(claimsSet));
    }

    @Test
    @DisplayName("roles의 파싱값이 비어있으면 예외를 던진다")
    void fail_shouldThrowInsufficientAuthenticationException_whenRolesIsEmpty()
        throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString())).willReturn(Collections.emptyList());

      // when & then
      assertThrows(
          InsufficientAuthenticationException.class, () -> jwtUtils.getAuthorities(claimsSet));
    }

    @Test
    @DisplayName("roles에 부적합한 권한이 와서 제거후 리스트가 비어있으면 예외를 던진다")
    void fail_shouldThrowBadCredentialsException_whenRoleIsInvalid() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString())).willReturn(List.of("ROLE_INVALID_ROLE"));

      // when & then
      assertThrows(BadCredentialsException.class, () -> jwtUtils.getAuthorities(claimsSet));
    }

    @Test
    @DisplayName("roles의 파싱이 성공한다면 GrantedAuthority 객체목록을 반환한다")
    void success_shouldReturnAuthorities_whenRolesClaimIsValid() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString()))
          .willReturn(List.of("ROLE_USER", "ROLE_INVALID_ROLE"));

      // when
      Collection<? extends GrantedAuthority> actual = jwtUtils.getAuthorities(claimsSet);

      // then
      assertThat(actual)
          .hasSize(1)
          .extracting(GrantedAuthority::getAuthority)
          .containsExactly("ROLE_USER");
    }
  }
}
