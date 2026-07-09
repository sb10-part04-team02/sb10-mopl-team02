package com.team02.mopl.domain.auth.provider;

import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.AbstractUserDetailsAuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MoplAuthenticationProvider extends AbstractUserDetailsAuthenticationProvider {

  private final UserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final JwtRegistry jwtRegistry;

  @Override
  protected UserDetails retrieveUser(
      String username, UsernamePasswordAuthenticationToken authentication)
      throws AuthenticationException {
    try {
      // 우리 서비스에선 email(=username)
      return userDetailsService.loadUserByUsername(username);
    } catch (UsernameNotFoundException e) {
      throw new BadCredentialsException(e.getMessage(), e);
    }
  }

  @Override
  protected void additionalAuthenticationChecks(
      UserDetails userDetails, UsernamePasswordAuthenticationToken authentication)
      throws AuthenticationException {

    MoplUserDetails moplUserDetails = (MoplUserDetails) userDetails;
    UUID userId = moplUserDetails.getUserDto().id();

    // 일반 로그인과 임시비밀번호 로그인 API가 동일해서
    // 어쩔수없이 redis의 임시패스워드부터 검사를 진행합니다

    // 1차 검사 (임시 패스워드)
    String tempPassword = jwtRegistry.getTempPassword(userId);
    if (tempPassword != null) {
      if (tempPassword.equals(authentication.getCredentials().toString())) {
        // 임시비밀번호 삭제
        jwtRegistry.deleteTempPassword(userId);
        return;
      }

      throw new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다");
    }

    // 2차 검사 (일반 패스워드)
    if (passwordEncoder.matches(
        authentication.getCredentials().toString(), // 평문
        moplUserDetails.getPassword())) { // 저장된 암호화된 패스워드
      return;
    }

    throw new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다");
  }
}
