package com.team02.mopl.domain.auth.jwt.token;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

  private Object principal;
  private final String credentials;

  public JwtAuthenticationToken(String token) {
    super(null);
    this.credentials = token;
    setAuthenticated(false);
  }

  public JwtAuthenticationToken(
      Object principal, String token, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = principal;
    this.credentials = token;
    super.setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return this.credentials;
  }

  @Override
  public Object getPrincipal() {
    return this.principal;
  }
}
