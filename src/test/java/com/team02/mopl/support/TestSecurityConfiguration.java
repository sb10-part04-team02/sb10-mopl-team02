package com.team02.mopl.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

// @EnableMethodSecurity는 슬라이스 테스트환경에서 적용이 제대로 안됩니다
// 권한 테스트를 진행할땐 아래의 authorizeHttpRequests항목에 URL 추가해서
// PreAuthorize가 적용되었다고 가정하고 테스트 진행 바랍니다
@TestConfiguration
public class TestSecurityConfiguration {
  @Bean
  public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()) // csrf 제거
        .authorizeHttpRequests(
            auth ->
                auth
                    // 어드민권한 테스트용
                    .requestMatchers(HttpMethod.GET, "/api/users")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/users/{userId}/role")
                    .hasRole("ADMIN")

                    // 위의 어드민 권한용API 제외하고 전부 허용
                    .anyRequest()
                    .permitAll());
    return http.build();
  }
}
