package com.team02.mopl.domain.auth.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.token.JwtAuthenticationToken;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationProvider implements AuthenticationProvider {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtUtils jwtUtils;
  private final JwtRegistry jwtRegistry;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String token = (String) authentication.getCredentials();

    // 값을 반환했다는 것 자체가 검증이 성공됨을 의미
    JWTClaimsSet claimsSet = jwtTokenProvider.verifyAccessToken(token);

    // 블랙리스트 검사
    if (jwtRegistry.isBlacklisted(claimsSet.getJWTID())) {
      throw new CredentialsExpiredException("이미 로그아웃된 토큰입니다.");
    }

    UUID userId = jwtUtils.getUserId(claimsSet);
    Collection<? extends GrantedAuthority> authorities = jwtUtils.getAuthorities(claimsSet);

    // TODO: 계정잠금된 계정은 로그인 불가 기능 추가

    // @AuthenticationPrincipal사용을 UUID타입 userId을 사용하기로 되어있음
    return new JwtAuthenticationToken(userId, token, authorities);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return JwtAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
