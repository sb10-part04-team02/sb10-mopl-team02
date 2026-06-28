package com.team02.mopl.domain.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.nimbusds.jwt.JWTClaimsSet;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProviderTest {

  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtUtils jwtUtils;
  @InjectMocks private JwtAuthenticationProvider jwtAuthenticationProvider;

  @Test
  @DisplayName("유효한 토큰이면 authentication token을 반환한다")
  void success_shouldReturnAuthenticationToken_whenTokenIsValid() {
    // given
    String token = "accessToken";
    JWTClaimsSet mockClaimSet = mock(JWTClaimsSet.class);
    given(jwtTokenProvider.verifyAccessToken(token)).willReturn(mockClaimSet);

    Authentication authentication = mock(Authentication.class);
    given(authentication.getCredentials()).willReturn(token);

    UUID userId = UUID.randomUUID();
    given(jwtUtils.getUserId(any(JWTClaimsSet.class))).willReturn(userId);

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
