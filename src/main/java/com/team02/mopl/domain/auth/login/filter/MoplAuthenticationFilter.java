package com.team02.mopl.domain.auth.login.filter;

import static org.springframework.http.HttpMethod.POST;

import com.team02.mopl.domain.auth.dto.SignInRequest;
import com.team02.mopl.domain.auth.login.token.MoplAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.security.authentication.AuthenticationServiceException;
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
      ConstraintViolation<SignInRequest> firstViolation = violations.iterator().next();
      throw new AuthenticationServiceException(firstViolation.getMessage());
    }

    MoplAuthenticationToken authToken = new MoplAuthenticationToken(email, password);

    return getAuthenticationManager().authenticate(authToken);
  }
}
