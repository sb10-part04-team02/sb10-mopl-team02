package com.team02.mopl.domain.auth.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.securityContext;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.auth.dto.JwtDto;
import com.team02.mopl.domain.auth.dto.ResetPasswordRequest;
import com.team02.mopl.domain.auth.dto.SignInRequest;
import com.team02.mopl.domain.auth.exception.AuthException;
import com.team02.mopl.domain.auth.jwt.handler.JwtLogoutHandler;
import com.team02.mopl.domain.auth.jwt.utils.JwtUtils;
import com.team02.mopl.domain.auth.service.AuthService;
import com.team02.mopl.domain.auth.service.AuthService.TokenResult;
import com.team02.mopl.domain.auth.service.MailService;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.global.config.SecurityConfig;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
// 테스트 실행마다 스프링 컨테이너, 시큐리티 환경 다시 빌드
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthControllerTest {

  @MockitoBean private AuthenticationEntryPoint jwtAuthenticationEntryPoint;
  @MockitoBean private AuthenticationSuccessHandler jwtLoginSuccessHandler;
  @MockitoBean private AuthenticationFailureHandler jwtLoginFailureHandler;
  @MockitoBean private AuthenticationManager authenticationManager;
  @MockitoBean private JwtLogoutHandler jwtLogoutHandler;
  @MockitoBean private AuthService authService;
  @MockitoBean private JwtUtils jwtUtils;
  @MockitoBean private MailService mailService;

  @Autowired private AuthController authController;
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
  class SignIn {

    @Test
    @DisplayName("authController의 signIn함수를 거치면 에러를 반환한다")
    void fail_shouldThrowException_whenAuthControllerInvokeFunction() {
      // given
      SignInRequest request = new SignInRequest("example@gmail.com", "password");

      // when & then
      assertThrows(AuthException.class, () -> authController.signIn(request));
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

  @Nested
  class SignOut {
    @Test
    @DisplayName("authController의 signOut함수를 거치면 예외를 던진다")
    void fail_shouldThrowException_whenAuthControllerInvokeFunction() {
      // when & then
      assertThrows(AuthException.class, authController::signOut);
    }

    @Test
    @DisplayName("csrf토큰이 없으면 403을 반환한다")
    void fail_shouldReturn403Forbidden_whenNoCsrfToken() throws Exception {
      // when & then
      mockMvc.perform(post("/api/auth/sign-out")).andExpect(status().isForbidden());
    }

    private static Stream<Arguments> provideTokens() {
      return Stream.of(
          Arguments.of(null, "Refresh", "존재하지 않는 Access"),
          Arguments.of("Access", null, "존재하지 않는 Refresh"),
          Arguments.of("Access", "Refresh", "정상"));
    }

    @ParameterizedTest
    @MethodSource("provideTokens")
    @DisplayName("토큰들이 있어도 없어도 204를 반환한다")
    void success_shouldReturn204AndExpireCookie_whenTokensAreValidOrMissing(
        String accessToken, String refreshToken, String description) throws Exception {
      // given
      MockHttpServletRequestBuilder builder = post("/api/auth/sign-out").with(csrf());

      if (accessToken != null) {
        builder.header("Authorization", "Bearer " + accessToken);
      }
      if (refreshToken != null) {
        Cookie refershCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, refreshToken);
        builder.cookie(refershCookie);
      }

      willAnswer(
              invocation -> {
                HttpServletResponse response = invocation.getArgument(1); // 두 번째 인자인 response 꺼내기
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                Cookie mockCookie = new Cookie(JwtUtils.REFRESH_TOKEN_COOKIE_NAME, "");
                mockCookie.setMaxAge(0);

                response.addCookie(mockCookie);
                return null;
              })
          .given(jwtLogoutHandler)
          .logout(any(), any(), any());

      // when & then
      mockMvc
          .perform(builder)
          .andExpect(status().isNoContent())
          .andExpect(
              header()
                  .string(
                      HttpHeaders.SET_COOKIE,
                      containsString(JwtUtils.REFRESH_TOKEN_COOKIE_NAME + "=;")))
          .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

      then(jwtLogoutHandler).should(times(1)).logout(any(), any(), any());
    }
  }

  @Nested
  class Refresh {

    @Test
    @DisplayName("refresh 토큰이 없으면 401 에러를 반환한다")
    void fail_shouldReturn401Unauthorized_whenRefreshTokenIsNull() throws Exception {
      // when & then
      mockMvc
          .perform(post("/api/auth/refresh").with(csrf()))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.exceptionName").value("AuthenticationRequiredException"))
          .andExpect(jsonPath("$.message").value("인증 쿠키가 누락되었습니다."));
    }

    @Test
    @DisplayName("정상적인 refresh 토큰이 들어오는 경우 200 JwtDto를 반환한다")
    void success_shouldReturn200OkAndJwtDto_whenRefreshTokenIsValid() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      String email = "example@gmail.com";
      UserDto userDto = new UserDto(userId, Instant.now(), email, "이름", null, Role.USER, false);
      String accessToken = "accessToken";
      JwtDto jwtDto = new JwtDto(userDto, accessToken);
      String comingRefreshToken = "coming refreshToken";
      String newRefreshToken = "newRefreshToken";

      TokenResult mockTokenResult = mock(TokenResult.class);
      given(authService.update(comingRefreshToken)).willReturn(mockTokenResult);
      given(mockTokenResult.refreshToken()).willReturn(newRefreshToken);
      given(mockTokenResult.jwtDto()).willReturn(jwtDto);

      String refreshTokenName = JwtUtils.REFRESH_TOKEN_COOKIE_NAME;
      ResponseCookie responseCookie =
          ResponseCookie.from(refreshTokenName, newRefreshToken).build();
      given(jwtUtils.generateRefreshTokenCookie(newRefreshToken)).willReturn(responseCookie);

      // when & then
      mockMvc
          .perform(
              post("/api/auth/refresh")
                  .with(csrf())
                  .cookie(new Cookie(refreshTokenName, comingRefreshToken)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userDto.id").value(userId.toString()))
          .andExpect(jsonPath("$.accessToken").value(accessToken))
          .andExpect(cookie().value(refreshTokenName, newRefreshToken));

      then(authService).should(times(1)).update(comingRefreshToken);
    }
  }

  @Nested
  class ResetPassword {
    @Test
    @DisplayName("비밀번호 초기화를 성공적으로 수행한다면 200을 반환한다")
    void success_shouldReturn200_whenValidRequest() throws Exception {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest("example@gmail.com");
      String content = objectMapper.writeValueAsString(request);

      // when & then
      mockMvc
          .perform(createResetPasswordRequest(content))
          .andDo(print())
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("이메일 형태가 맞지 않는다면 400을 반환한다")
    void fail_shouldReturn400_whenInvalidEmailIsProvided() throws Exception {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest("invalid-email");
      String content = objectMapper.writeValueAsString(request);

      // when & then
      mockMvc
          .perform(createResetPasswordRequest(content))
          .andDo(print())
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("유저를 찾을수 없다면 404을 반환한다")
    void fail_shouldReturn404_whenUserNotFound() throws Exception {
      // given
      ResetPasswordRequest request = new ResetPasswordRequest("example@gmail.com");
      String content = objectMapper.writeValueAsString(request);
      willThrow(new UserNotFoundException()).given(mailService).sendResetPasswordEmail(request);

      // when & then
      mockMvc
          .perform(createResetPasswordRequest(content))
          .andDo(print())
          .andExpect(status().isNotFound());
    }

    private MockHttpServletRequestBuilder createResetPasswordRequest(String content) {

      return MockMvcRequestBuilders.post("/api/auth/reset-password")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content(content);
    }
  }
}
