package com.team02.mopl.domain.auth.jwt.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
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
import org.springframework.security.core.GrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JwtUtilsTest {

  @Mock private JwtProperties jwtProperties;
  @InjectMocks private JwtUtils jwtUtils;

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
  }

  @Nested
  class GetUserId {
    @Test
    @DisplayName("claimSet의 파싱이 실패한다면 에러를 던진다")
    void fail_shouldThrowException_whenClaimSetIsInvalid() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringClaim(anyString())).willThrow(ParseException.class);

      // when & then
      assertThrows(RuntimeException.class, () -> jwtUtils.getUserId(claimsSet));
    }

    @Test
    @DisplayName("claimSet의 파싱이 성공한다면 UUID를 반환한다")
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
    @DisplayName("claimSet의 파싱이 실패한다면 빈 권한목록을 반환한다")
    void fail_shouldReturnEmptyList_whenParseExceptionOccurs() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString())).willThrow(ParseException.class);

      // when
      Collection<? extends GrantedAuthority> authorities = jwtUtils.getAuthorities(claimsSet);

      // then
      assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("role 클레임 자체가 없을 때 빈 권한목록을 반환한다")
    void fail_shouldReturnEmptyList_whenRolesClaimIsNull() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString())).willReturn(null);

      // when
      Collection<? extends GrantedAuthority> authorities = jwtUtils.getAuthorities(claimsSet);

      // then
      assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("role 클레임에 권한이 비어있으면 빈 권한목록을 반환한다")
    void fail_shouldReturnEmptyList_whenRolesClaimIsEmpty() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      given(claimsSet.getStringListClaim(anyString())).willReturn(Collections.emptyList());

      // when
      Collection<? extends GrantedAuthority> authorities = jwtUtils.getAuthorities(claimsSet);

      // then
      assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("올바른 권한목록이 있으면 GrantedAuthority 객체목록을 반환한다")
    void success_shouldReturnAuthorities_whenRolesClaimIsValid() throws ParseException {
      // given
      JWTClaimsSet claimsSet = mock(JWTClaimsSet.class);
      doReturn(List.of("ROLE_USER")).when(claimsSet).getStringListClaim(anyString());

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
