package com.team02.mopl.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtRegistry.AuthCheckResult;
import com.team02.mopl.domain.auth.jwt.token.JwtAuthenticationToken;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProviderTest {

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtUtils jwtUtils;
  @Mock private JwtRegistry jwtRegistry;
  @InjectMocks private JwtAuthenticationProvider jwtAuthenticationProvider;

  @Test
  @DisplayName("유효한 토큰이 오지 않으면 예외를 던진다")
  void fail_shouldThrowAuthenticationException_whenTokenIsInvalid() {
    // given
    Authentication mockAuth = mock(Authentication.class);
    given(mockAuth.getCredentials()).willReturn("InvalidToken");
    given(jwtTokenProvider.verifyAccessToken(anyString())).willThrow(BadCredentialsException.class);

    // when & then
    assertThrows(
        AuthenticationException.class, () -> jwtAuthenticationProvider.authenticate(mockAuth));
  }

  @Test
  @DisplayName("토큰ID가 블랙리스트에 올라와 있다면 예외를 던진다")
  void fail_shouldThrowCredentialsExpiredException_whenTokenIsBlacklisted() {
    // given
    Authentication mockAuth = mock(Authentication.class);
    given(mockAuth.getCredentials()).willReturn("ValidToken");

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);

    given(jwtUtils.getUserId(mockClaimSet)).willReturn(UUID.randomUUID());
    given(mockClaimSet.getJWTID()).willReturn(UUID.randomUUID().toString());

    AuthCheckResult authResult = new AuthCheckResult(true, false, false);
    given(jwtRegistry.checkAuthStatus(anyString(), any(UUID.class), any())).willReturn(authResult);

    // when & then
    assertThrows(
        CredentialsExpiredException.class, () -> jwtAuthenticationProvider.authenticate(mockAuth));
  }

  @Test
  @DisplayName("유저ID가 잠금처리 되어있다면 예외를 던진다")
  void fail_shouldThrowLockedException_whenUserIsLocked() {
    // given
    Authentication mockAuth = mock(Authentication.class);
    given(mockAuth.getCredentials()).willReturn("ValidToken");

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);

    given(jwtUtils.getUserId(mockClaimSet)).willReturn(UUID.randomUUID());
    given(mockClaimSet.getJWTID()).willReturn(UUID.randomUUID().toString());

    AuthCheckResult authResult = new AuthCheckResult(false, true, false);
    given(jwtRegistry.checkAuthStatus(anyString(), any(UUID.class), any())).willReturn(authResult);

    // when & then
    assertThrows(LockedException.class, () -> jwtAuthenticationProvider.authenticate(mockAuth));
  }

  @Test
  @DisplayName("액세스토큰이 만료되었다면 예외를 던진다")
  void fail_shouldThrowCredentialsExpiredException_whenAccessTokenIsInactive() {
    // given
    Authentication mockAuth = mock(Authentication.class);
    given(mockAuth.getCredentials()).willReturn("ValidToken");

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);

    given(jwtUtils.getUserId(mockClaimSet)).willReturn(UUID.randomUUID());
    given(mockClaimSet.getJWTID()).willReturn(UUID.randomUUID().toString());

    AuthCheckResult authResult = new AuthCheckResult(false, false, false);
    given(jwtRegistry.checkAuthStatus(anyString(), any(UUID.class), any())).willReturn(authResult);

    // when & then
    assertThrows(
        CredentialsExpiredException.class, () -> jwtAuthenticationProvider.authenticate(mockAuth));
  }

  @Test
  @DisplayName("userId 값을 얻는데 실패한다면 예외를 던진다")
  void fail_shouldThrowAuthenticationException_whenGetUserIdFails() {
    // given
    Authentication mockAuth = mock(Authentication.class);
    given(mockAuth.getCredentials()).willReturn("ValidToken");

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);

    // userId 얻는데 예외 발생
    given(jwtUtils.getUserId(mockClaimSet)).willThrow(BadCredentialsException.class);

    // when & then
    assertThrows(
        AuthenticationException.class, () -> jwtAuthenticationProvider.authenticate(mockAuth));
  }

  @Test
  @DisplayName("권한 리스트를 얻는데 실패한다면 예외를 던진다")
  void fail_shouldThrowAuthenticationException_whenGetAuthoritiesFails() {
    // given
    Authentication mockAuth = mock(Authentication.class);
    given(mockAuth.getCredentials()).willReturn("ValidToken");

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(anyString())).willReturn(mockClaimSet);

    given(jwtUtils.getUserId(mockClaimSet)).willReturn(UUID.randomUUID());
    given(mockClaimSet.getJWTID()).willReturn(UUID.randomUUID().toString());

    AuthCheckResult authResult = new AuthCheckResult(false, false, true);
    given(jwtRegistry.checkAuthStatus(anyString(), any(UUID.class), any())).willReturn(authResult);

    // 권한 얻는데 예외 발생
    given(jwtUtils.getAuthorities(mockClaimSet)).willThrow(BadCredentialsException.class);

    // when & then
    assertThrows(
        AuthenticationException.class, () -> jwtAuthenticationProvider.authenticate(mockAuth));
  }

  @Test
  @DisplayName("유효한 토큰이면 authentication token을 반환한다")
  void success_shouldReturnAuthenticationToken_whenTokenIsValid() {
    // given
    String token = "accessToken";
    Authentication authentication = mock(Authentication.class);
    given(authentication.getCredentials()).willReturn(token);

    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(token)).willReturn(mockClaimSet);

    UUID userId = UUID.randomUUID();
    given(jwtUtils.getUserId(mockClaimSet)).willReturn(userId);

    AuthCheckResult authResult = new AuthCheckResult(false, false, true);
    given(mockClaimSet.getJWTID()).willReturn(UUID.randomUUID().toString());
    given(jwtRegistry.checkAuthStatus(anyString(), any(UUID.class), any())).willReturn(authResult);

    Collection<? extends GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_USER"));
    doReturn(authorities).when(jwtUtils).getAuthorities(any(JWTClaimsSet.class));

    // when
    Authentication actual = jwtAuthenticationProvider.authenticate(authentication);

    // then
    assertThat(actual.getPrincipal()).isEqualTo(userId);
    assertThat(actual.getCredentials()).isEqualTo(token);
    assertThat(actual.getAuthorities()).isEqualTo(authorities);
  }

  @Test
  @DisplayName("인증타입을 지원하면 true를 반환한다")
  void success_shouldReturnTrue_whenAuthenticationIsValid() {
    // when
    boolean actual = jwtAuthenticationProvider.supports(JwtAuthenticationToken.class);

    // then
    assertThat(actual).isEqualTo(true);
  }
}
