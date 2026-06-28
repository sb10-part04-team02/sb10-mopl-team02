package com.team02.mopl.domain.auth.jwt.utils;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtProperties;
import java.text.ParseException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtUtils {

  private static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";
  private final JwtProperties properties;

  public ResponseCookie generateRefreshTokenCookie(String refreshToken) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
        .path("/")
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .maxAge(properties.refreshTokenExpiration())
        .build();
  }

  public UUID getUserId(JWTClaimsSet claimsSet) {
    try {
      return UUID.fromString(claimsSet.getStringClaim("userId"));
    } catch (ParseException e) {
      // TODO:  Exception을 어떻게 처리해야할지 나중에 구현(임시)
      throw new RuntimeException("Token을 파싱하는데 실패했습니다.", e);
    }
  }

  public Collection<? extends GrantedAuthority> getAuthorities(JWTClaimsSet claimsSet) {
    try {
      List<String> roles = claimsSet.getStringListClaim("roles");

      if (roles == null || roles.isEmpty()) {
        return Collections.emptyList();
      }

      return roles.stream().map(SimpleGrantedAuthority::new).toList();

    } catch (ParseException e) {
      return Collections.emptyList();
    }
  }
}
