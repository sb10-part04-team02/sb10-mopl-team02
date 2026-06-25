package com.team02.mopl.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@TestConfiguration
public class TestSecurityConfiguration {
  @Bean
  public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()) // csrf 제거
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll()); // 권한 전부 허용
    return http.build();
  }
}
