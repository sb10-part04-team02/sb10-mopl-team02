package com.team02.mopl.domain.user.controller;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserSearchRequest;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.service.UserService;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@WebMvcTest(UserController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "ADMIN")
public class UserControllerSecurityTest {

  @MockitoBean private UserService userService;
  @Autowired private MockMvc mockMvc;

  @Nested
  class GetUsers {

    private MultiValueMap<String, String> params;

    @BeforeEach
    void setUp() {
      // 필수 항목
      params = new LinkedMultiValueMap<>();
      params.set("limit", "20");
      params.set("sortDirection", SortDirection.ASCENDING.name());
      params.set("sortBy", UserSortBy.NAME.name());
    }

    @Test
    @DisplayName("전체 파라미터를 가지고 요청시 200 CursorResponse<UserDto>를 반환한다")
    void success_shouldReturnCursorResponse_whenAllParamsPresent() throws Exception {
      // given
      String email = "example@gmail.com";
      CursorResponse<UserDto> response = createBasicCursorResponseUserDto(email);
      params.set("emailLike", email);
      params.set("roleEqual", Role.USER.name());
      params.set("isLocked", "false");
      params.set("cursor", "CURSOR");
      params.set("idAfter", UUID.randomUUID().toString());
      given(userService.getUsers(any(UserSearchRequest.class))).willReturn(response);

      // when & then
      mockMvc
          .perform(createGetUserListRequest(params))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").value(notNullValue()))
          .andExpect(jsonPath("$.data").value(notNullValue()))
          .andExpect(jsonPath("$.nextCursor").value(nullValue()))
          .andExpect(jsonPath("$.nextIdAfter").value(nullValue()))
          .andExpect(jsonPath("$.totalCount").value(response.data().size()))
          .andExpect(jsonPath("$.sortBy").value(notNullValue()))
          .andExpect(jsonPath("$.sortDirection").value(notNullValue()));
    }

    @Test
    @DisplayName("필수 파라미터가 있을 경우 200을 반환한다")
    void success_shouldReturn200_whenOnlyRequiredParamsPresent() throws Exception {
      // given
      CursorResponse<UserDto> response = createBasicCursorResponseUserDto("example@gmail.com");
      given(userService.getUsers(any(UserSearchRequest.class))).willReturn(response);

      // when & then
      mockMvc
          .perform(createGetUserListRequest(params))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").value(notNullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"limit", "sortDirection", "sortBy"})
    @DisplayName("필수 파라미터가 누락된 경우 400을 반환한다")
    void fail_shouldReturn400_whenRequiredParamIsMissing(String missingParam) throws Exception {
      // given
      params.remove(missingParam);

      // when & then
      mockMvc.perform(createGetUserListRequest(params)).andExpect(status().isBadRequest());
    }

    static Stream<Arguments> provideUserSearchRequestParams() {
      return Stream.of(
          // roleEqual, isLocked, idAfter, sortDirection, sortBy, description
          Arguments.of("UsEr", null, null, null, null, "roleEqual invalid"),
          Arguments.of(null, "f", null, null, null, "isLocked invalid"),
          Arguments.of(null, null, "123", null, null, "idAfter invalid"),
          Arguments.of(null, null, null, "Mixed", null, "sortDirection invalid"),
          Arguments.of(null, null, null, null, "NaMe", "sortBy invalid"));
    }

    @ParameterizedTest
    @MethodSource("provideUserSearchRequestParams")
    @DisplayName("타입이 올바르지 않으면 400을 반환한다")
    void fail_shouldReturn400_whenParameterFormatsAreInvalid(
        Object roleEqual,
        Object isLocked,
        Object idAfter,
        Object sortDirection,
        Object sortBy,
        Object description)
        throws Exception {
      // given
      if (roleEqual != null) params.set("roleEqual", roleEqual.toString());
      if (isLocked != null) params.set("isLocked", isLocked.toString());
      if (idAfter != null) params.set("idAfter", idAfter.toString());
      if (sortDirection != null) params.set("sortDirection", sortDirection.toString());
      if (sortBy != null) params.set("sortBy", sortBy.toString());

      // when & then
      mockMvc.perform(createGetUserListRequest(params)).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER권한으로 요청하면 403을 반환한다")
    void fail_shouldReturn403_whenUserHasInsufficientRole() throws Exception {
      // when & then
      mockMvc.perform(createGetUserListRequest(params)).andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder createGetUserListRequest(
        MultiValueMap<String, String> params) {
      return MockMvcRequestBuilders.get("/api/users").params(params);
    }

    private CursorResponse<UserDto> createBasicCursorResponseUserDto(String email) {
      UserDto userDto =
          new UserDto(UUID.randomUUID(), Instant.now(), email, "이름", null, Role.USER, false);
      List<UserDto> users = List.of(userDto);
      return new CursorResponse<>(users, null, null, false, users.size(), "name", "ASCENDING");
    }
  }
}
