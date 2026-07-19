package com.team02.mopl.global.config;

import com.team02.mopl.domain.auth.jwt.JwtAuthenticationProvider;
import com.team02.mopl.domain.auth.jwt.filter.JwtAuthenticationFilter;
import com.team02.mopl.domain.auth.jwt.handler.JwtLoginFailureHandler;
import com.team02.mopl.domain.auth.jwt.handler.JwtLoginSuccessHandler;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.auth.login.filter.MoplAuthenticationFilter;
import com.team02.mopl.domain.auth.login.provider.MoplAuthenticationProvider;
import com.team02.mopl.domain.auth.oauth.handler.OAuthLoginFailureHandler;
import com.team02.mopl.domain.auth.oauth.handler.OAuthLoginSuccessHandler;
import com.team02.mopl.domain.auth.oauth.service.MoplOidcUserService;
import com.team02.mopl.global.config.auth.handler.SpaCsrfTokenRequestHandler;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
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

  private final MoplAuthenticationProvider moplAuthenticationProvider;
  private final JwtAuthenticationProvider jwtAuthenticationProvider;
  private final JwtLoginSuccessHandler jwtLoginSuccessHandler;
  private final JwtLoginFailureHandler jwtLoginFailureHandler;
  private final LogoutHandler jwtLogoutHandler;
  private final AuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final MoplOidcUserService moplOidcUserService;
  private final OAuthLoginSuccessHandler oAuthLoginSuccessHandler;
  private final OAuthLoginFailureHandler oAuthLoginFailureHandler;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtUtils jwtUtils, Validator validator)
      throws Exception {
    RequestMatcher apiMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    RequestMatcher nonApiMatcher = new NegatedRequestMatcher(apiMatcher);

    http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .oauth2Login(
            oauth ->
                oauth
                    // 소셜기능은 로그인할때만 사용하기에 OAuth토큰을 저장할 필요가 없음
                    .userInfoEndpoint(info -> info.oidcUserService(moplOidcUserService))
                    .successHandler(oAuthLoginSuccessHandler)
                    .failureHandler(oAuthLoginFailureHandler))
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
                    .requestMatchers(HttpMethod.POST, "/api/auth/reset-password")
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

    // jwt 토큰 검증 및 provider
    JwtAuthenticationFilter jwtAuthenticationFilter =
        new JwtAuthenticationFilter(jwtUtils, jwtAuthenticationEntryPoint);
    http.authenticationProvider(jwtAuthenticationProvider)
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    // 일반/임시 패스워드 로그인 필터 및 provider
    MoplAuthenticationFilter moplAuthenticationFilter = getMoplAuthenticationFilter(validator);
    http.authenticationProvider(moplAuthenticationProvider)
        .addFilterAt(moplAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    // filterChain 빌드
    SecurityFilterChain securityFilterChain = http.build();

    // securityFilterChian이 build가 된 이후에야 authenticationManager를 불러올 수 있음
    AuthenticationManager authenticationManager = http.getSharedObject(AuthenticationManager.class);
    jwtAuthenticationFilter.setAuthenticationManager(authenticationManager);
    moplAuthenticationFilter.setAuthenticationManager(authenticationManager);

    return securityFilterChain;
  }

  private MoplAuthenticationFilter getMoplAuthenticationFilter(Validator validator) {
    MoplAuthenticationFilter filter = new MoplAuthenticationFilter(validator);
    filter.setAuthenticationSuccessHandler(jwtLoginSuccessHandler);
    filter.setAuthenticationFailureHandler(jwtLoginFailureHandler);
    return filter;
  }
}
