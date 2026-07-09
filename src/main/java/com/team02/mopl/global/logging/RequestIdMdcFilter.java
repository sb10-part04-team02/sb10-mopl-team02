package com.team02.mopl.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 UUID requestId를 MDC에 넣어 한 요청에서 발생한 로그를 묶어 추적할 수 있게 하는 필터.
 *
 * <p>시큐리티 필터 체인(order -100)보다 앞에 위치해야 인증 실패 로그에도 requestId가 남는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdMdcFilter extends OncePerRequestFilter {

  public static final String MDC_KEY = "requestId";
  public static final String HEADER_NAME = "X-Request-Id";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER_NAME, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }
}
