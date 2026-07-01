package com.team02.mopl.domain.auth.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.auth.dto.JwtDto;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtLoginSuccessHandler implements AuthenticationSuccessHandler {

  private final ObjectMapper objectMapper;
  private final JwtTokenProvider jwtTokenProvider;
  private final JwtRegistry jwtRegistry;
  private final JwtUtils jwtUtils;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {

    response.setCharacterEncoding("UTF-8");
    response.setContentType("application/json");

    final Object object;
    if (authentication.getPrincipal() instanceof MoplUserDetails userDetails) {
      String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
      String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

      // refresh 토큰 Redis 등록
      jwtRegistry.registerRefreshToken(userDetails.getUserDto().id(), refreshToken);

      // refresh 토큰 쿠키 등록
      ResponseCookie cookie = jwtUtils.generateRefreshTokenCookie(refreshToken);
      response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

      response.setStatus(HttpServletResponse.SC_OK);
      object = new JwtDto(userDetails.getUserDto(), accessToken);
    } else {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      object = new ErrorResponse("BadCredentialsException", "인증 객체 타입이 맞지 않습니다.", null);
    }

    response.getWriter().write(objectMapper.writeValueAsString(object));
  }
}
