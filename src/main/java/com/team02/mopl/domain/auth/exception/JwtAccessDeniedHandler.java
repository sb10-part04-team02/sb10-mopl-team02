package com.team02.mopl.domain.auth.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

// 필터 단계 AccessDeniedException을 잡아 프로젝트 표준 ErrorResponse JSON으로 403 응답
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    log.warn(
        "인가실패: uri={}, exception={}", request.getRequestURI(), accessDeniedException.getMessage());

    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    ErrorResponse errorResponse = new ErrorResponse("AuthorizationException", "권한이 부족합니다", null);
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
