package com.team02.mopl.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.global.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("쿠키에 CSRF 토큰이 없으면 발급이 진행된다")
  void success_shouldIssueToken_whenCsrfTokenIsAbsent() throws Exception {
    // when & then
    mockMvc
        .perform(get("/api/auth/csrf-token"))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"))
        .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN")));
  }

  @Test
  @DisplayName("잘못된 메소드로 요청을 하면 Method 에러를 반환한다")
  void fail_shouldReturnMethodIsNotAllowed_whenInvalidMethodRequest() throws Exception {
    // when & then
    mockMvc.perform(post("/api/auth/csrf-token")).andExpect(status().is4xxClientError());
  }
}
