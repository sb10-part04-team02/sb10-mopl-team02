package com.team02.mopl.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.service.UserService;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
class UserControllerNormalTest {

  @MockitoBean private UserService userService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void setUp() {}

  @AfterEach
  void tearDown() {}

  @TestInstance(Lifecycle.PER_CLASS)
  @Nested
  class CreateUser {

    private final String name = "username";
    private final String email = "example@gmail.com";
    private final String password = "12345678";

    private Stream<Arguments> provideInvalidUserCreateRequests() {

      return Stream.of(
          Arguments.of(new UserCreateRequest("", email, password), "이름 누락"),
          Arguments.of(new UserCreateRequest("1", email, password), "이름 길이 미달(2자 미만)"),
          Arguments.of(new UserCreateRequest(name, "", password), "이메일 누락"),
          Arguments.of(new UserCreateRequest(name, "invalid email", password), "이메일 형식 오류"),
          Arguments.of(new UserCreateRequest(name, email, ""), "비밀번호 누락"),
          Arguments.of(new UserCreateRequest(name, email, "1234"), "비밀번호 길이 미달(8자 미만)"),
          Arguments.of(
              new UserCreateRequest(name, email, "123456789012345678901"), "비밀번호 길이 초과(20 초과)"));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserCreateRequests")
    @DisplayName("유효하지 않은 회원가입 요청은 400 BadRequest를 반환한다")
    void fail_shouldReturnBadRequest_whenRequestIsInvalid(
        UserCreateRequest invalidRequest, String description) throws Exception {
      // when & then
      mockMvc
          .perform(
              post("/api/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일이 중복일 때 409 에러를 반환한다")
    void fail_shouldReturn409_whenEmailIsDuplicate() throws Exception {
      // given
      UserCreateRequest request = new UserCreateRequest(name, email, password);
      given(userService.createUser(any(UserCreateRequest.class)))
          .willThrow(new UserEmailDuplicateException());

      // when & then
      mockMvc
          .perform(
              post("/api/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.exceptionName").value("UserEmailDuplicateException"))
          .andExpect(jsonPath("$.message").value("이미 존재하는 이메일입니다."));
    }

    @Test
    @DisplayName("정상적인 파라미터가 오면 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenRequestIsValid() throws Exception {
      // given
      String name = "username";
      String email = "example@gmail.com";
      UserCreateRequest request = new UserCreateRequest(name, email, "12345678");
      UserDto expect =
          new UserDto(UUID.randomUUID(), Instant.now(), email, name, null, Role.USER, false);
      given(userService.createUser(any(UserCreateRequest.class))).willReturn(expect);

      // when & then
      mockMvc
          .perform(
              post("/api/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.name").value(name))
          .andExpect(jsonPath("$.email").value(email));
    }
  }
}
