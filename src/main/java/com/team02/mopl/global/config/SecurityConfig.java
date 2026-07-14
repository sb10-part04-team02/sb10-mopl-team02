package com.team02.mopl.global.config;

import com.team02.mopl.domain.auth.jwt.JwtAuthenticationProvider;
import com.team02.mopl.domain.auth.jwt.filter.JwtAuthenticationFilter;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.global.config.auth.handler.SpaCsrfTokenRequestHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

  private final AuthenticationSuccessHandler jwtLoginSuccessHandler;
  private final AuthenticationFailureHandler jwtLoginFailureHandler;
  private final LogoutHandler jwtLogoutHandler;
  private final AuthenticationEntryPoint jwtAuthenticationEntryPoint;

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, AuthenticationManager authenticationManager, JwtUtils jwtUtils)
      throws Exception {
    RequestMatcher apiMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    RequestMatcher nonApiMatcher = new NegatedRequestMatcher(apiMatcher);

    http
        // 수동으로 만든걸 추가해야 formLogin에서 Provider가 제대로 인식됨
        .authenticationManager(authenticationManager)
        .addFilterBefore(
            new JwtAuthenticationFilter(
                jwtUtils, authenticationManager, jwtAuthenticationEntryPoint),
            UsernamePasswordAuthenticationFilter.class)
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(
            login ->
                login
                    .loginProcessingUrl("/api/auth/sign-in")
                    .successHandler(jwtLoginSuccessHandler)
                    .failureHandler(jwtLoginFailureHandler))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/sign-out")
                    .addLogoutHandler(jwtLogoutHandler)
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        .authorizeHttpRequests(
            auth ->
                auth
                    // 예외 URL
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/users")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/sign-in")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/sign-out")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/auth/refresh")
                    .permitAll()

                    // Actuator: 헬스체크(Docker HEALTHCHECK)와 Prometheus 스크레이프 대상만 열고
                    // 나머지 엔드포인트(env, beans 등)는 nonApiMatcher permitAll보다 먼저 차단
                    .requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/actuator/prometheus")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .requestMatchers(nonApiMatcher)
                    .permitAll() // swagger, api-docs 대응

                    // 외의 것들은 인증 필요
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            except ->
                except
                    // 토큰이 없거나(익명), 인증에 실패한 채로 보호된 리소스에 접근할 때
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint));
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      HttpSecurity http,
      UserDetailsService userDetailsService,
      PasswordEncoder passwordEncoder,
      JwtAuthenticationProvider jwtAuthenticationProvider)
      throws Exception {

    AuthenticationManagerBuilder builder = http.getSharedObject(AuthenticationManagerBuilder.class);

    // DaoAuthenticationProvider(일반 로그인용)
    builder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder);
    // JwtAuthenticationProvider
    builder.authenticationProvider(jwtAuthenticationProvider);

    return builder.build();
  }
}
