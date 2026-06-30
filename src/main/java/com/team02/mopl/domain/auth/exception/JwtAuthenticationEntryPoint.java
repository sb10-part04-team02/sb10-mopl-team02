package com.team02.mopl.domain.auth.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper =
        (objectMapper != null) ? objectMapper.copy() : new ObjectMapper(); // spotbugsMain EL2 해결책
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    log.info("인증실패: exception={}", authException.getMessage());
    log.debug("인증실패 상세 스택", authException);

    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    ErrorResponse errorResponse = new ErrorResponse("AuthenticationException", "인증이 실패했습니다.", null);
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
