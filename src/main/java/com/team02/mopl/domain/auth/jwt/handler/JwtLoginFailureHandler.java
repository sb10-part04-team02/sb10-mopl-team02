package com.team02.mopl.domain.auth.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtLoginFailureHandler implements AuthenticationFailureHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException, ServletException {

    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");

    String exceptionName = "BadCredentialsException";
    int status = HttpServletResponse.SC_UNAUTHORIZED;
    Map<String, String> details = null;

    if (exception instanceof AuthenticationServiceException servEx) {
      exceptionName = "BadRequestException";
      status = HttpServletResponse.SC_BAD_REQUEST;
      details = Map.of("reason", servEx.getMessage());
    }

    response.setStatus(status);
    ErrorResponse errorResponse = new ErrorResponse(exceptionName, "인증이 실패했습니다.", details);
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
