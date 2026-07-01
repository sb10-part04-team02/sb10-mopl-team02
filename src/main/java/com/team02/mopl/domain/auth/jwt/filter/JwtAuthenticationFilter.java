package com.team02.mopl.domain.auth.jwt.filter;

import com.team02.mopl.domain.auth.jwt.token.JwtAuthenticationToken;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtils jwtUtils;
  private final AuthenticationManager authenticationManager;
  private final AuthenticationEntryPoint authenticationEntryPoint;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String accessToken = jwtUtils.resolveAccessToken(request.getHeader("Authorization"));
    if (StringUtils.hasText(accessToken)) {
      try {
        // 인증 전 토큰
        JwtAuthenticationToken authToken = new JwtAuthenticationToken(accessToken);

        // 인증된 토큰
        Authentication authentication = authenticationManager.authenticate(authToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);

      } catch (AuthenticationException e) {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, e);
        return;
      }
    }

    filterChain.doFilter(request, response);
  }
}
