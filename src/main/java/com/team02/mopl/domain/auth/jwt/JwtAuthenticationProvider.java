package com.team02.mopl.domain.auth.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtRegistry.AuthCheckResult;
import com.team02.mopl.domain.auth.jwt.token.JwtAuthenticationToken;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.LockedException;
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
    UUID userId = jwtUtils.getUserId(claimsSet);

    AuthCheckResult result = jwtRegistry.checkAuthStatus(claimsSet.getJWTID(), userId);
    if (result.isBlacklisted()) {
      throw new CredentialsExpiredException("이미 로그아웃된 토큰입니다.");
    }

    if (result.isUserLocked()) {
      throw new LockedException("잠금처리된 유저입니다. 어드민에게 문의하세요.");
    }

    Collection<? extends GrantedAuthority> authorities = jwtUtils.getAuthorities(claimsSet);

    // @AuthenticationPrincipal사용을 UUID타입 userId을 사용하기로 되어있음
    return new JwtAuthenticationToken(userId, token, authorities);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return JwtAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
