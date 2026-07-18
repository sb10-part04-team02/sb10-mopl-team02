package com.team02.mopl.domain.auth.login.provider;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.login.token.MoplAuthenticationToken;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MoplAuthenticationProvider implements AuthenticationProvider {

  private final UserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final JwtRegistry jwtRegistry;

  private static final String EMAIL_OR_PASSWORD_NOT_MATCHES = "이메일 또는 비밀번호가 일치하지 않습니다";

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {

    String email = authentication.getPrincipal().toString();
    String password = authentication.getCredentials().toString();

    MoplUserDetails userDetails;
    try {
      userDetails = (MoplUserDetails) userDetailsService.loadUserByUsername(email);
    } catch (UsernameNotFoundException e) {
      throw new BadCredentialsException(EMAIL_OR_PASSWORD_NOT_MATCHES);
    }

    if (!userDetails.isAccountNonLocked()) {
      throw new LockedException("잠금처리된 유저입니다. 어드민에게 문의하세요.");
    }

    UUID userId = userDetails.getUserDto().id();

    // 임시비밀번호 검사
    String tempPassword = jwtRegistry.getTempPassword(userId);
    if (tempPassword != null) {
      // 1차 검사 (임시 패스워드)
      boolean isVerified = jwtRegistry.verifyAndUseTempPassword(userId, password);
      if (!isVerified) {
        throw new BadCredentialsException(EMAIL_OR_PASSWORD_NOT_MATCHES);
      }

    } else {
      // 2차 검사 (일반 패스워드)
      if (!passwordEncoder.matches(
          password, // 평문
          userDetails.getPassword())) { // 저장된 암호화된 패스워드
        throw new BadCredentialsException(EMAIL_OR_PASSWORD_NOT_MATCHES);
      }
    }

    return new MoplAuthenticationToken(userDetails, userDetails.getAuthorities());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return MoplAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
