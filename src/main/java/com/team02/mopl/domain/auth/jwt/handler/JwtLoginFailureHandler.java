package com.team02.mopl.domain.auth.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class JwtLoginFailureHandler implements AuthenticationFailureHandler {

  private final ObjectMapper objectMapper;

  public JwtLoginFailureHandler(ObjectMapper objectMapper) {
    this.objectMapper =
        (objectMapper != null) ? objectMapper.copy() : new ObjectMapper(); // spotbugsMain EL2 해결책
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {

    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    ErrorResponse errorResponse = new ErrorResponse("BadCredentialsException", "인증이 실패했습니다.", null);
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
