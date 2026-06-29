package com.team02.mopl.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.auth.dto.JwtDto;
import com.team02.mopl.domain.auth.dto.SignInRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.global.config.SecurityConfig;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
// 테스트 실행마다 스프링 컨테이너, 시큐리티 환경 다시 빌드
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

  @MockitoBean private AuthenticationSuccessHandler jwtLoginSuccessHandler;
  @MockitoBean private AuthenticationFailureHandler jwtLoginFailureHandler;
  @MockitoBean private AuthenticationManager authenticationManager;

  @Autowired private ObjectMapper objectMapper;
  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("쿠키에 CSRF 토큰이 없으면 발급이 진행된다")
  void success_shouldIssueToken_whenCsrfTokenIsAbsent() throws Exception {
    // when & then
    mockMvc
        .perform(
            get("/api/auth/csrf-token")
                // mockMvc 프레임워크에선 쿠키를 임시로 구워둘 공간이 필요
                .with(securityContext(SecurityContextHolder.createEmptyContext())))
        .andExpect(status().isNoContent())
        .andExpect(header().exists("Set-Cookie"))
        .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN")));
  }

  @Test
  @WithMockUser // 가짜 유저
  @DisplayName("잘못된 메소드로 요청을 하면 Method 에러를 반환한다")
  void fail_shouldReturnMethodIsNotAllowed_whenInvalidMethodRequest() throws Exception {
    // when & then
    mockMvc
        .perform(post("/api/auth/csrf-token").with(csrf())) // 가짜 csrf
        .andExpect(status().isMethodNotAllowed());
  }

  @Nested
  class signIn {

    @Test
    @DisplayName("authController의 signIn함수를 거치면 에러를 반환한다")
    void fail_shouldThrowException_whenAuthControllerInvokeFunction() {
      // given
      AuthController authController = new AuthController();
      SignInRequest request = new SignInRequest("example@gmail.com", "password");

      // when & then
      assertThrows(IllegalStateException.class, () -> authController.signIn(request));
    }

    @Test
    @DisplayName("csrf토큰이 없으면 403을 반환한다")
    void fail_shouldReturn403Forbidden_whenNoCsrfToken() throws Exception {
      // when & then
      mockMvc
          .perform(
              post("/api/auth/sign-in")
                  .param("username", "example@gmail.com")
                  .param("password", "password"))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("이메일을 가진 계정이 없어 로그인에 실패한다")
    void fail_shouldReturnUnauthorized_whenEmailIsAbsent() throws Exception {
      // given
      given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException(""));
      willAnswer(
              invocation -> {
                HttpServletResponse response = invocation.getArgument(1); // 두 번째 인자인 response 꺼내기
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"exceptionName\":\"BadCredentialsException\"}");
                return null;
              })
          .given(jwtLoginFailureHandler)
          .onAuthenticationFailure(any(), any(), any());

      // when & then
      mockMvc
          .perform(
              post("/api/auth/sign-in")
                  .with(csrf())
                  .param("username", "example@gmail.com")
                  .param("password", "password"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.exceptionName").value("BadCredentialsException"));
      then(jwtLoginFailureHandler).should(times(1)).onAuthenticationFailure(any(), any(), any());
    }

    @Test
    @DisplayName("이메일로 로그인에 성공한다")
    void success_shouldSignIn_whenEmailIsValid() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      String email = "example@gmail.com";
      UserDto userDto = new UserDto(userId, Instant.now(), email, "이름", null, Role.USER, false);
      String accessToken = "accessToken";
      Object object = new JwtDto(userDto, accessToken);

      Authentication mockAuthentication = mock(Authentication.class);
      given(authenticationManager.authenticate(any())).willReturn(mockAuthentication);

      willAnswer(
              invocation -> {
                HttpServletResponse response = invocation.getArgument(1); // 두 번째 인자인 response 꺼내기
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(objectMapper.writeValueAsString(object));
                return null;
              })
          .given(jwtLoginSuccessHandler)
          .onAuthenticationSuccess(any(), any(), any());

      // when & then
      mockMvc
          .perform(
              post("/api/auth/sign-in")
                  .with(csrf())
                  .param("username", email)
                  .param("password", "password"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userDto.id").value(userId.toString()))
          .andExpect(jsonPath("$.accessToken").value(accessToken));
      then(jwtLoginSuccessHandler).should(times(1)).onAuthenticationSuccess(any(), any(), any());
    }
  }
}
