package com.team02.mopl.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.dto.JwtDto;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.exception.CompromisedTokenException;
import com.team02.mopl.domain.auth.exception.InvalidTokenException;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtRegistry.RotationResult;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.auth.service.AuthService.TokenResult;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserLockedException;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserDetailsService userDetailsService;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtRegistry jwtRegistry;
  @Mock private JwtUtils jwtUtils;
  @InjectMocks private AuthService authService;

  @Nested
  class Update {

    @Test
    @DisplayName("refresh 토큰이 들어오면 JwtDto와 새로운 Refresh토큰을 반환한다")
    void success_shouldReturnJwtDtoAndNewRefresh_whenRefreshTokenIsGiven() {
      // given
      JWTClaimsSet mockClaims = mock(JWTClaimsSet.class);
      given(jwtTokenProvider.verifyRefreshToken(anyString())).willReturn(mockClaims);

      UUID userId = UUID.randomUUID();
      given(jwtUtils.getUserId(mockClaims)).willReturn(userId);

      String email = "example@gmail.com";
      given(mockClaims.getSubject()).willReturn(email);

      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      given(userDetailsService.loadUserByUsername(anyString())).willReturn(mockUserDetails);
      given(mockUserDetails.isAccountNonLocked()).willReturn(true);

      String refresh = "refresh";
      String newAccess = "new Access";
      String newRefresh = "new Refresh";
      given(jwtTokenProvider.generateAccessToken(mockUserDetails)).willReturn(newAccess);
      given(jwtTokenProvider.generateRefreshToken(mockUserDetails)).willReturn(newRefresh);
      given(jwtRegistry.rotateRefreshToken(userId, refresh, newRefresh))
          .willReturn(RotationResult.OK);

      UserDto userDto = new UserDto(userId, Instant.now(), email, "이름", null, Role.USER, false);
      given(mockUserDetails.getUserDto()).willReturn(userDto);
      JwtDto jwtDto = new JwtDto(userDto, newAccess);
      TokenResult expect = new TokenResult(jwtDto, newRefresh);

      // when
      TokenResult actual = authService.update(refresh);

      // then
      assertThat(actual.jwtDto().accessToken()).isEqualTo(expect.jwtDto().accessToken());
      assertThat(actual.refreshToken()).isEqualTo(expect.refreshToken());
    }

    @Test
    @DisplayName("AuthenticationException에러가 발생하면 userId를 얻고 refresh토큰 삭제 시도를 한다")
    void fail_shouldTryDeleteRefreshToken_whenAuthenticationExceptionOccurs() {
      // given
      given(jwtTokenProvider.verifyRefreshToken(anyString()))
          .willThrow(BadCredentialsException.class);

      UUID userId = UUID.randomUUID();
      String invalidRefresh = "invalid refresh";
      JWTClaimsSet mockClaims = mock(JWTClaimsSet.class);
      given(jwtTokenProvider.parseClaimsWithoutVerification(invalidRefresh)).willReturn(mockClaims);
      given(jwtUtils.getUserId(mockClaims)).willReturn(userId);

      // when & then
      assertThrows(InvalidTokenException.class, () -> authService.update(invalidRefresh));
      then(jwtRegistry).should(times(1)).deleteRefreshToken(eq(userId), eq(invalidRefresh));
    }

    @Test
    @DisplayName("parsing도 불가능한 token이면 refresh토큰 삭제시도는 하지않고 예외를 던진다")
    void fail_shouldThrowExceptionAndNotDeleteToken_whenTokenParsingFails() {
      // given
      given(jwtTokenProvider.verifyRefreshToken(anyString()))
          .willThrow(BadCredentialsException.class);

      String invalidRefresh = "invalid refresh";
      given(jwtTokenProvider.parseClaimsWithoutVerification(invalidRefresh))
          .willThrow(BadCredentialsException.class);

      // when & then
      assertThrows(InvalidTokenException.class, () -> authService.update(invalidRefresh));
      then(jwtRegistry).should(never()).deleteRefreshToken(any(), any());
    }

    @Test
    @DisplayName("서비스에 email을 가진 유저가 없는경우 refresh토큰 삭제를 하고 예외를 던진다")
    void fail_shouldDeleteRefreshToken_whenUserNotFound() {
      // given
      JWTClaimsSet mockClaims = mock(JWTClaimsSet.class);
      given(jwtTokenProvider.verifyRefreshToken(anyString())).willReturn(mockClaims);

      UUID userId = UUID.randomUUID();
      given(jwtUtils.getUserId(mockClaims)).willReturn(userId);

      String notFoundEmail = "not-found@gmail.com";
      given(mockClaims.getSubject()).willReturn(notFoundEmail);
      given(userDetailsService.loadUserByUsername(notFoundEmail))
          .willThrow(UsernameNotFoundException.class);

      String refresh = "refresh";

      // when & then
      assertThrows(UserNotFoundException.class, () -> authService.update(refresh));
      then(jwtRegistry).should(times(1)).deleteRefreshToken(eq(userId), eq(refresh));
    }

    @Test
    @DisplayName("계정이 잠금상태라면 예외를 던진다")
    void fail_shouldThrowException_whenUserIsLocked() {
      // given
      JWTClaimsSet mockClaims = mock(JWTClaimsSet.class);
      given(jwtTokenProvider.verifyRefreshToken(anyString())).willReturn(mockClaims);

      UUID userId = UUID.randomUUID();
      given(jwtUtils.getUserId(mockClaims)).willReturn(userId);

      String email = "example@gmail.com";
      given(mockClaims.getSubject()).willReturn(email);

      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      given(userDetailsService.loadUserByUsername(email)).willReturn(mockUserDetails);
      given(mockUserDetails.isAccountNonLocked()).willReturn(false);

      String refresh = "refresh";

      // when & then
      assertThrows(UserLockedException.class, () -> authService.update(refresh));
      then(jwtRegistry).should(times(1)).deleteAllRefreshToken(eq(userId));
    }

    @Test
    @DisplayName("토큰이 탈취가 됐으면 예외를 던진다")
    void fail_shouldThrowCompromisedTokenException_whenTokenIsCompromised() {
      // given
      JWTClaimsSet mockClaims = mock(JWTClaimsSet.class);
      given(jwtTokenProvider.verifyRefreshToken(anyString())).willReturn(mockClaims);

      UUID userId = UUID.randomUUID();
      given(jwtUtils.getUserId(mockClaims)).willReturn(userId);

      String email = "example@gmail.com";
      given(mockClaims.getSubject()).willReturn(email);

      MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);
      given(userDetailsService.loadUserByUsername(email)).willReturn(mockUserDetails);
      given(mockUserDetails.isAccountNonLocked()).willReturn(true);

      String refresh = "refresh";
      String newAccess = "newAccess";
      String newRefresh = "newRefresh";
      given(jwtTokenProvider.generateAccessToken(mockUserDetails)).willReturn(newAccess);
      given(jwtTokenProvider.generateRefreshToken(mockUserDetails)).willReturn(newRefresh);
      given(jwtRegistry.rotateRefreshToken(userId, refresh, newRefresh))
          .willReturn(RotationResult.COMPROMISED);

      // when & then
      assertThrows(CompromisedTokenException.class, () -> authService.update(refresh));
    }
  }
}
