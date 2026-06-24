package com.team02.mopl.global.config;

import com.team02.mopl.global.config.auth.handler.SpaCsrfTokenRequestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http.csrf(
            csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
        .authorizeHttpRequests(
            auth ->
                auth
                    // 예외 URL
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf-token")
                    .permitAll()

                    // 외의 것들은 인증 필요
                    .anyRequest()
                    .authenticated())
        .build();
  }
}
