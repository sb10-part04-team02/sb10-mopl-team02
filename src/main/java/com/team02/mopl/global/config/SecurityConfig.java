package com.team02.mopl.global.config;

import com.team02.mopl.domain.auth.jwt.JwtAuthenticationProvider;
import com.team02.mopl.domain.auth.jwt.filter.JwtAuthenticationFilter;
import com.team02.mopl.global.config.auth.handler.SpaCsrfTokenRequestHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
  private final AuthenticationEntryPoint jwtAuthenticationEntryPoint;

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http, AuthenticationManager authenticationManager) throws Exception {
    RequestMatcher apiMatcher = PathPatternRequestMatcher.withDefaults().matcher("/api/**");
    RequestMatcher nonApiMatcher = new NegatedRequestMatcher(apiMatcher);

    http
        // 수동으로 만든걸 추가해야 formLogin에서 Provider가 제대로 인식됨
        .authenticationManager(authenticationManager)
        .addFilterBefore(
            new JwtAuthenticationFilter(authenticationManager),
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
        .authorizeHttpRequests(
            auth ->
                auth
                    // 예외 URL
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/users")
                    .permitAll()
                    .requestMatchers("/api/auth/sign-in")
                    .permitAll()
                    .requestMatchers(nonApiMatcher)
                    .permitAll() // swagger, api-docs 대응

                    // 외의 것들은 인증 필요
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(except -> except.authenticationEntryPoint(jwtAuthenticationEntryPoint));
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
