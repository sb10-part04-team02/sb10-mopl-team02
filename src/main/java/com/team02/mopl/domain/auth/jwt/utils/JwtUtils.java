package com.team02.mopl.domain.auth.jwt.utils;

import com.nimbusds.jwt.JWTClaimsSet;
import com.team02.mopl.domain.auth.jwt.JwtProperties;
import com.team02.mopl.domain.user.entity.enums.Role;
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
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class JwtUtils {

  public static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";
  private final JwtProperties properties;

  public String resolveAccessToken(String bearerToken) {
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7);
    }

    return null;
  }

  public ResponseCookie generateRefreshTokenCookie(String refreshToken) {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
        .path("/")
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .maxAge(properties.refreshTokenExpiration())
        .build();
  }

  public ResponseCookie generateLogoutRefreshTokenCookie() {
    return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME)
        .path("/")
        .httpOnly(true)
        .secure(true)
        .sameSite("Lax")
        .maxAge(0)
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

      List<SimpleGrantedAuthority> authorities =
          roles.stream().filter(this::isValidRole).map(SimpleGrantedAuthority::new).toList();
      if (authorities.isEmpty()) {
        throw new BadCredentialsException("허용되지 않은 권한이 포함되었습니다.");
      }

      return authorities;

    } catch (ParseException e) {
      throw new BadCredentialsException("Token의 role 클레임을 파싱하는데 실패했습니다.", e);
    }
  }

  private boolean isValidRole(String role) {
    try {
      String cleanRole = role.replace("ROLE_", "");
      Role.valueOf(cleanRole);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
