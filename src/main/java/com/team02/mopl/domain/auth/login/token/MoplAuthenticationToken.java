package com.team02.mopl.domain.auth.login.token;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

public class MoplAuthenticationToken extends AbstractAuthenticationToken {

  private final Object principal;
  private final Object credentials;

  // 로그인 요청
  public MoplAuthenticationToken(String email, String password) {
    super(null);
    this.principal = email;
    this.credentials = password;
    setAuthenticated(false);
  }

  // 로그인 성공
  public MoplAuthenticationToken(
      MoplUserDetails userDetails, Collection<? extends GrantedAuthority> authorities) {
    super(authorities);
    this.principal = userDetails;
    this.credentials = null;
    setAuthenticated(true);
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
