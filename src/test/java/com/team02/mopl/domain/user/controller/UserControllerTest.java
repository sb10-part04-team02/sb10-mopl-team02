package com.team02.mopl.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.service.UserService;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

  @MockitoBean private UserService userService;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void setUp() {}

  @AfterEach
  void tearDown() {}

  @Nested
  class CreateUser {

    private static Stream<Arguments> provideInvalidUserCreateRequests() {
      String name = "username";
      String email = "example@gmail.com";
      String password = "12345678";

      return Stream.of(
          Arguments.of(new UserCreateRequest("", email, password), "이름 누락"),
          Arguments.of(new UserCreateRequest("1", email, password), "이름 길이 미달(2자 미만)"),
          Arguments.of(new UserCreateRequest(name, "", password), "이메일 누락"),
          Arguments.of(new UserCreateRequest(name, "invalid email", password), "이메일 형식 오류"),
          Arguments.of(new UserCreateRequest(name, email, ""), "비밀번호 누락"),
          Arguments.of(new UserCreateRequest(name, email, "1234"), "비밀번호 길이 미달(8자 미만)"));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidUserCreateRequests")
    @WithMockUser
    @DisplayName("유효하지 않은 회원가입 요청은 400 BadRequest를 반환한다")
    void fail_shouldReturnBadRequest_whenRequestIsInvalid(
        UserCreateRequest invalidRequest, String description) throws Exception {
      // when & then
      mockMvc
          .perform(
              post("/api/users")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("정상적인 파라미터가 오면 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenValidParameters() throws Exception {
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
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andDo(print())
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.name").value(name))
          .andExpect(jsonPath("$.email").value(email));
    }
  }
}
