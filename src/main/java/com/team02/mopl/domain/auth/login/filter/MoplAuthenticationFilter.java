package com.team02.mopl.domain.auth.login.filter;

import static org.springframework.http.HttpMethod.POST;

import com.team02.mopl.domain.auth.dto.SignInRequest;
import com.team02.mopl.domain.auth.login.token.MoplAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

public class MoplAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

  private final Validator validator;

  public MoplAuthenticationFilter(Validator validator) {
    super(PathPatternRequestMatcher.withDefaults().matcher(POST, "/api/auth/sign-in"));
    this.validator = validator;
  }

  @Override
  public Authentication attemptAuthentication(
      HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

    String email = request.getParameter("username");
    String password = request.getParameter("password");

    // SignInRequestDto의 제약 활용
    SignInRequest signInRequest = new SignInRequest(email, password);
    Set<ConstraintViolation<SignInRequest>> violations = validator.validate(signInRequest);
    if (!violations.isEmpty()) {
      throw new BadCredentialsException("이메일 또는 비밀번호가 일치하지 않습니다.");
    }

    MoplAuthenticationToken authToken = new MoplAuthenticationToken(email, password);

    return getAuthenticationManager().authenticate(authToken);
  }
}
