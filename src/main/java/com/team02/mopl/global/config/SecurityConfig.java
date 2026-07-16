package com.team02.mopl.global.config;

import com.team02.mopl.domain.auth.jwt.JwtAuthenticationProvider;
import com.team02.mopl.domain.auth.jwt.filter.JwtAuthenticationFilter;
import com.team02.mopl.domain.auth.jwt.handler.JwtLoginFailureHandler;
import com.team02.mopl.domain.auth.jwt.handler.JwtLoginSuccessHandler;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.auth.oauth.handler.OAuthLoginFailureHandler;
import com.team02.mopl.domain.auth.oauth.handler.OAuthLoginSuccessHandler;
import com.team02.mopl.domain.auth.oauth.service.MoplOidcUserService;
import com.team02.mopl.domain.auth.provider.MoplAuthenticationProvider;
import com.team02.mopl.global.config.auth.handler.SpaCsrfTokenRequestHandler;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
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

  private final JwtLoginSuccessHandler jwtLoginSuccessHandler;
  private final JwtLoginFailureHandler jwtLoginFailureHandler;
  private final LogoutHandler jwtLogoutHandler;
  private final AuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final MoplOidcUserService oidcUserService;
  private final OAuthLoginSuccessHandler oAuthLoginSuccessHandler;
  private final OAuthLoginFailureHandler oAuthLoginFailureHandler;

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
        .oauth2Login(
            oauth ->
                oauth
                    // 소셜기능은 로그인할때만 사용하기에 OAuth토큰을 저장할 필요가 없음
                    .successHandler(oAuthLoginSuccessHandler)
                    .failureHandler(oAuthLoginFailureHandler))
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
    return http.build();
  }

  @Bean
  public AuthenticationManager authenticationManager(
      MoplAuthenticationProvider moplAuthenticationProvider,
      JwtAuthenticationProvider jwtAuthenticationProvider) {

    // OAuth2용 토큰 클라이언트 생성
    RestClientAuthorizationCodeTokenResponseClient tokenResponseClient =
        new RestClientAuthorizationCodeTokenResponseClient();

    // oidcUserservice는 커스텀
    OidcAuthorizationCodeAuthenticationProvider oidcProvider =
        new OidcAuthorizationCodeAuthenticationProvider(tokenResponseClient, oidcUserService);

    return new ProviderManager(
        Arrays.asList(
            moplAuthenticationProvider, // 일반 로그인 + 임시비밀번호 포함
            jwtAuthenticationProvider, // 토큰용
            oidcProvider // OIDC 소셜 로그인용(테스트)
            ));
  }
}
