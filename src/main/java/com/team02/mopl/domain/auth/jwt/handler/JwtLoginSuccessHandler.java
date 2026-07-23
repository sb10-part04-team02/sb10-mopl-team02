package com.team02.mopl.domain.auth.jwt.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.auth.dto.JwtDto;
import com.team02.mopl.domain.auth.entity.MoplUserDetails;
import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.auth.jwt.JwtTokenProvider;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
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

    if (!(authentication.getPrincipal() instanceof MoplUserDetails userDetails)) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      ErrorResponse error =
          new ErrorResponse("BadCredentialsException", "인증 객체 타입이 맞지 않습니다.", null);
      response.getWriter().write(objectMapper.writeValueAsString(error));
      return;
    }

    // 토큰 발급
    String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
    String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

    try {
      // 토큰 redis에 등록
      jwtRegistry.registerToken(userDetails.getUserDto().id(), refreshToken, accessToken);
    } catch (BusinessException e) {
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      ErrorResponse error = new ErrorResponse("InternalServerException", "서버 내부 오류가 발생했습니다.", null);
      response.getWriter().write(objectMapper.writeValueAsString(error));
      return;
    } catch (BadCredentialsException e) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      ErrorResponse error = new ErrorResponse("BadCredentialsException", "유효한 토큰이 아닙니다.", null);
      response.getWriter().write(objectMapper.writeValueAsString(error));
      return;
    }

    // refresh 토큰 쿠키 등록
    ResponseCookie cookie = jwtUtils.generateRefreshTokenCookie(refreshToken);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    response.setStatus(HttpServletResponse.SC_OK);
    JwtDto jwtDto = new JwtDto(userDetails.getUserDto(), accessToken);
    response.getWriter().write(objectMapper.writeValueAsString(jwtDto));
  }
}
