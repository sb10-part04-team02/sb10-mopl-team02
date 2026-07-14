package com.team02.mopl.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.user.dto.ChangePasswordRequest;
import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserUpdateRequest;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.exception.UserForbiddenException;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.service.UserService;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.time.Instant;
import java.util.List;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(UserController.class)
@Import({TestSecurityConfiguration.class})
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

    private static final String name = "username";
    private static final String email = "example@gmail.com";
    private static final String password = "12345678";

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
      given(userService.createUser(any(UserCreateRequest.class), any(Role.class)))
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
      given(userService.createUser(any(UserCreateRequest.class), any(Role.class)))
          .willReturn(expect);

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

  @Nested
  class GetUser {

    @Test
    @DisplayName("사용자 상세 조회가 성공하면 200 OK와 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenUserExists() throws Exception {
      UUID userId = UUID.randomUUID();
      UserDto response =
          new UserDto(
              userId,
              Instant.parse("2026-07-01T00:00:00Z"),
              "woody@mopl.io",
              "우디",
              "https://example.com/profile.png",
              Role.USER,
              false);

      given(userService.getUser(userId)).willReturn(response);

      mockMvc
          .perform(get("/api/users/{userId}", userId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(userId.toString()))
          .andExpect(jsonPath("$.email").value("woody@mopl.io"))
          .andExpect(jsonPath("$.name").value("우디"))
          .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"))
          .andExpect(jsonPath("$.role").value("USER"))
          .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 사용자를 조회하면 404 Not Found를 반환한다")
    void fail_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
      UUID userId = UUID.randomUUID();

      given(userService.getUser(userId)).willThrow(new UserNotFoundException());

      mockMvc
          .perform(get("/api/users/{userId}", userId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.exceptionName").value("UserNotFoundException"));
    }

    @Test
    @DisplayName("사용자 ID가 UUID 형식이 아니면 400 Bad Request를 반환한다")
    void fail_shouldReturnBadRequest_whenUserIdIsInvalidUuid() throws Exception {
      mockMvc
          .perform(get("/api/users/{userId}", "invalid-user-id"))
          .andExpect(status().isBadRequest());

      then(userService).shouldHaveNoInteractions();
    }
  }

  @Nested
  class UpdateProfile {

    @Test
    @DisplayName("프로필 수정이 성공하면 200 OK와 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenRequestIsValid() throws Exception {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest updateRequest = new UserUpdateRequest("새이름");
      UserDto response =
          new UserDto(
              userId,
              Instant.parse("2026-07-02T00:00:00Z"),
              "woody@mopl.io",
              "새이름",
              "https://example.com/profile.png",
              Role.USER,
              false);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request",
              "",
              MediaType.APPLICATION_JSON_VALUE,
              objectMapper.writeValueAsBytes(updateRequest));

      given(
              userService.updateProfile(
                  eq(userId), eq(userId), any(UserUpdateRequest.class), isNull()))
          .willReturn(response);

      mockMvc
          .perform(
              multipart("/api/users/{userId}", userId)
                  .file(requestPart)
                  .with(
                      servletRequest -> {
                        servletRequest.setMethod("PATCH");
                        return servletRequest;
                      })
                  .with(authentication(authenticationWithPrincipal(userId)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(userId.toString()))
          .andExpect(jsonPath("$.name").value("새이름"))
          .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"));

      then(userService)
          .should()
          .updateProfile(eq(userId), eq(userId), any(UserUpdateRequest.class), isNull());
    }

    @Test
    @DisplayName("다른 사용자의 프로필을 수정하면 403 Forbidden을 반환한다")
    void fail_shouldReturnForbidden_whenRequesterIsNotOwner() throws Exception {
      UUID requesterId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UserUpdateRequest updateRequest = new UserUpdateRequest("새이름");

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request",
              "",
              MediaType.APPLICATION_JSON_VALUE,
              objectMapper.writeValueAsBytes(updateRequest));

      given(
              userService.updateProfile(
                  eq(requesterId), eq(userId), any(UserUpdateRequest.class), isNull()))
          .willThrow(new UserForbiddenException());

      mockMvc
          .perform(
              multipart("/api/users/{userId}", userId)
                  .file(requestPart)
                  .with(
                      servletRequest -> {
                        servletRequest.setMethod("PATCH");
                        return servletRequest;
                      })
                  .with(authentication(authenticationWithPrincipal(requesterId)))
                  .with(csrf()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.exceptionName").value("UserForbiddenException"));
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 프로필을 수정하면 404 Not Found를 반환한다")
    void fail_shouldReturnNotFound_whenUserDoesNotExist() throws Exception {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest updateRequest = new UserUpdateRequest("새이름");

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request",
              "",
              MediaType.APPLICATION_JSON_VALUE,
              objectMapper.writeValueAsBytes(updateRequest));

      given(
              userService.updateProfile(
                  eq(userId), eq(userId), any(UserUpdateRequest.class), isNull()))
          .willThrow(new UserNotFoundException());

      mockMvc
          .perform(
              multipart("/api/users/{userId}", userId)
                  .file(requestPart)
                  .with(
                      servletRequest -> {
                        servletRequest.setMethod("PATCH");
                        return servletRequest;
                      })
                  .with(authentication(authenticationWithPrincipal(userId)))
                  .with(csrf()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.exceptionName").value("UserNotFoundException"));
    }

    @Test
    @DisplayName("프로필 수정 요청의 이름이 비어 있으면 400 Bad Request를 반환한다")
    void fail_shouldReturnBadRequest_whenNameIsBlank() throws Exception {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest updateRequest = new UserUpdateRequest("");

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request",
              "",
              MediaType.APPLICATION_JSON_VALUE,
              objectMapper.writeValueAsBytes(updateRequest));

      mockMvc
          .perform(
              multipart("/api/users/{userId}", userId)
                  .file(requestPart)
                  .with(
                      servletRequest -> {
                        servletRequest.setMethod("PATCH");
                        return servletRequest;
                      })
                  .with(authentication(authenticationWithPrincipal(userId)))
                  .with(csrf()))
          .andExpect(status().isBadRequest());

      then(userService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("프로필 이미지가 포함된 수정 요청이면 image 파트를 서비스로 전달한다")
    void success_shouldPassImageToService_whenImagePartExists() throws Exception {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest updateRequest = new UserUpdateRequest("새이름");
      UserDto response =
          new UserDto(
              userId,
              Instant.parse("2026-07-02T00:00:00Z"),
              "woody@mopl.io",
              "새이름",
              "https://example.com/new-profile.png",
              Role.USER,
              false);

      MockMultipartFile requestPart =
          new MockMultipartFile(
              "request",
              "",
              MediaType.APPLICATION_JSON_VALUE,
              objectMapper.writeValueAsBytes(updateRequest));

      MockMultipartFile imagePart =
          new MockMultipartFile(
              "image", "profile.png", MediaType.IMAGE_PNG_VALUE, "image".getBytes());

      given(
              userService.updateProfile(
                  eq(userId), eq(userId), any(UserUpdateRequest.class), isA(MultipartFile.class)))
          .willReturn(response);

      mockMvc
          .perform(
              multipart("/api/users/{userId}", userId)
                  .file(requestPart)
                  .file(imagePart)
                  .with(
                      servletRequest -> {
                        servletRequest.setMethod("PATCH");
                        return servletRequest;
                      })
                  .with(authentication(authenticationWithPrincipal(userId)))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(userId.toString()))
          .andExpect(jsonPath("$.name").value("새이름"))
          .andExpect(jsonPath("$.profileImageUrl").value("https://example.com/new-profile.png"));

      then(userService)
          .should()
          .updateProfile(
              eq(userId), eq(userId), any(UserUpdateRequest.class), isA(MultipartFile.class));
    }
  }

  @Nested
  class UpdatePassword {
    @Test
    @DisplayName("패스워드 변경을 성공적으로 수행하면 204를 반환한다")
    void success_shouldReturn204_whenPasswordIsUpdatedSuccessfully() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      String content = objectMapper.writeValueAsString(new ChangePasswordRequest("validPassword"));

      // when & then
      mockMvc
          .perform(createChangePasswordRequest(userId, content, null))
          .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "123456789012345678901"})
    @DisplayName("유효하지 않은 자리수의 문자열이 오면 400을 반환한다")
    void fail_shouldReturn400_whenLengthIsInvalid(String invalidPassword) throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      String invalidContent =
          objectMapper.writeValueAsString(new ChangePasswordRequest(invalidPassword));

      // when & then
      mockMvc
          .perform(createChangePasswordRequest(userId, invalidContent, null))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("본인이 아닐경우 403을 반환한다")
    void fail_shouldReturn403Forbidden_whenRequestUserIsNotOwner() throws Exception {
      // given
      UUID userId = UUID.randomUUID();
      UUID requesterId = UUID.randomUUID();
      String content = objectMapper.writeValueAsString(new ChangePasswordRequest("validPassword"));

      willThrow(new BusinessException(ErrorCode.FORBIDDEN))
          .given(userService)
          .updatePassword(eq(userId), eq(requesterId), any(ChangePasswordRequest.class));

      // when & then
      mockMvc
          .perform(createChangePasswordRequest(requesterId, content, userId))
          .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder createChangePasswordRequest(
        UUID requesterId, String content, UUID anotherUserId) {
      TestingAuthenticationToken testAuth =
          new TestingAuthenticationToken(
              requesterId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

      UUID userId = anotherUserId != null ? anotherUserId : requesterId;

      return MockMvcRequestBuilders.patch("/api/users/{userId}/password", userId)
          .with(authentication(testAuth))
          .contentType(MediaType.APPLICATION_JSON)
          .content(content);
    }
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
