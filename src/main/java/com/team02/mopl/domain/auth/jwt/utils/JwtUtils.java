package com.team02.mopl.domain.auth.jwt.utils;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtProperties;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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
      String userIdStr = claimsSet.getStringClaim("userId");
      if (userIdStr == null) {
        throw new BadCredentialsException("Token 내에 userId가 존재하지 않습니다.");
      }

      return UUID.fromString(userIdStr);

    } catch (ParseException | IllegalArgumentException e) {
      throw new BadCredentialsException("Token의 userId를 파싱하는데 실패했습니다.", e);
    }
  }

  public Collection<? extends GrantedAuthority> getAuthorities(JWTClaimsSet claimsSet) {
    try {
      List<String> roles = claimsSet.getStringListClaim("roles");
      if (roles == null || roles.isEmpty()) {
        throw new InsufficientAuthenticationException("Token 내에 권한이 누락되었습니다.");
      }

      return roles.stream().map(SimpleGrantedAuthority::new).toList();

    } catch (ParseException e) {
      throw new BadCredentialsException("Token의 role 클레임을 파싱하는데 실패했습니다.", e);
    }
  }
}
