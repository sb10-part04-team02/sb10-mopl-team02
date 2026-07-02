package com.team02.mopl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserSearchRequest;
import com.team02.mopl.domain.user.dto.UserUpdateRequest;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.exception.UserForbiddenException;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock UserRepository userRepository;

  @Mock UserMapper userMapper;

  @InjectMocks UserService userService;

  @Captor ArgumentCaptor<User> userCaptor;

  @Spy private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  @Nested
  class CreateUser {
    private static final String name = "username";
    private static final String email = "example@gmail.com";
    private static final String password = "12345678";

    @Test
    @DisplayName("이메일이 중복일 때 409 에러를 반환한다")
    void fail_shouldReturn409_whenEmailIsDuplicate() {
      // given
      UserCreateRequest request = new UserCreateRequest(name, email, password);
      given(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).willReturn(true);

      // when & then
      assertThrows(UserEmailDuplicateException.class, () -> userService.createUser(request));
    }

    @Test
    @DisplayName("동시성 가입레이스로 DB 저장시 Unique제약조건이 위반되면 409 에러를 반환한다")
    void fail_shouldReturn409_whenDbUniqueConstraintViolated() {
      // given
      UserCreateRequest request = new UserCreateRequest(name, email, password);
      given(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).willReturn(false);
      given(userRepository.saveAndFlush(any(User.class)))
          .willThrow(new DataIntegrityViolationException("Duplicate Email"));

      // when & then
      assertThrows(UserEmailDuplicateException.class, () -> userService.createUser(request));
    }

    @Test
    @DisplayName("정상적인 회원가입 요청일 때 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenRequestIsValid() {
      // given
      UserCreateRequest request = new UserCreateRequest(name, email, password);
      User mockUser = new User(name, email, "encryptedPassword", null, Role.USER, false);
      UserDto expect =
          new UserDto(UUID.randomUUID(), Instant.now(), email, name, null, Role.USER, false);
      given(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).willReturn(false);
      given(userRepository.saveAndFlush(any(User.class))).willReturn(mockUser);
      given(userMapper.toDto(any(User.class))).willReturn(expect);

      // when
      UserDto actual = userService.createUser(request);

      // then
      assertThat(actual).isEqualTo(expect);
      then(userRepository).should().existsByEmailAndDeletedAtIsNull(anyString());
      then(userRepository).should().saveAndFlush(userCaptor.capture());
      then(userMapper).should().toDto(any(User.class));

      User savedUser = userCaptor.getValue();
      assertThat(savedUser.getPassword())
          .isNotEqualTo(request.password()); // 암호화된 패스워드와 평문 패스워드 다름 비교
      assertThat(passwordEncoder.matches(request.password(), savedUser.getPassword())).isTrue();
    }

    @Test
    @DisplayName("아이디가 2글자 이하인 이메일도 가입해서 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenEmailIsShort() {
      // given
      String shortEmail = "ab@gmail.com";
      UserCreateRequest request = new UserCreateRequest(name, shortEmail, password);
      User mockUser = new User(name, shortEmail, "encryptedPassword", null, Role.USER, false);
      UserDto expect =
          new UserDto(UUID.randomUUID(), Instant.now(), shortEmail, name, null, Role.USER, false);
      given(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).willReturn(false);
      given(userRepository.saveAndFlush(any(User.class))).willReturn(mockUser);
      given(userMapper.toDto(any(User.class))).willReturn(expect);

      // when
      UserDto actual = userService.createUser(request);

      // then
      assertThat(actual).isEqualTo(expect);
      assertThat(actual.email()).isEqualTo(shortEmail);
    }
  }

  @Nested
  class GetUser {

    @Test
    @DisplayName("존재하는 사용자를 조회하면 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenUserExists() {
      UUID userId = UUID.randomUUID();
      User user = new User("우디", "woody@mopl.io", "password", null, Role.USER, false);
      UserDto expect =
          new UserDto(
              userId,
              Instant.parse("2026-07-01T00:00:00Z"),
              "woody@mopl.io",
              "우디",
              null,
              Role.USER,
              false);

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
      given(userMapper.toDto(user)).willReturn(expect);

      UserDto actual = userService.getUser(userId);

      assertThat(actual).isEqualTo(expect);
      then(userRepository).should().findByIdAndDeletedAtIsNull(userId);
      then(userMapper).should().toDto(user);
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 사용자를 조회하면 UserNotFoundException이 발생한다")
    void fail_shouldThrowUserNotFoundException_whenUserDoesNotExist() {
      UUID userId = UUID.randomUUID();
      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

      assertThrows(UserNotFoundException.class, () -> userService.getUser(userId));

      then(userRepository).should().findByIdAndDeletedAtIsNull(userId);
    }
  }

  @Nested
  class GetUsers {
    @Test
    @DisplayName("올바른 검색 요청이 오면 유저목록 조회 후 CursorResponse를 반환한다")
    void success_shouldReturnCursorResponse_whenValidSearchRequestProvided() {
      // given
      UserSearchRequest request = mock(UserSearchRequest.class);

      // when
      CursorResponse<UserDto> actual = userService.getUsers(request);

      // then
      assertThat(actual)
          .isNotNull()
          .satisfies(
              act -> {
                assertThat(act.data()).hasSize(1);
                assertThat(act.hasNext()).isFalse();
                assertThat(act.totalCount()).isEqualTo(1);
                assertThat(act.sortBy()).isEqualTo(UserSortBy.NAME.getValue());
                assertThat(act.sortDirection()).isEqualTo(SortDirection.ASCENDING.name());
              });
    }
  }

  @Nested
  class UpdateProfile {

    @Test
    @DisplayName("본인이 프로필을 수정하면 이름을 변경하고 UserDto를 반환한다")
    void success_shouldUpdateNameAndReturnUserDto_whenRequesterIsOwner() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      User user =
          new User(
              "기존이름",
              "woody@mopl.io",
              "password",
              "https://example.com/profile.png",
              Role.USER,
              false);
      UserDto expect =
          new UserDto(
              userId,
              Instant.parse("2026-07-02T00:00:00Z"),
              "woody@mopl.io",
              "새이름",
              "https://example.com/profile.png",
              Role.USER,
              false);

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
      given(userMapper.toDto(user)).willReturn(expect);

      UserDto actual = userService.updateProfile(userId, userId, request, null);

      assertThat(actual).isEqualTo(expect);
      assertThat(user.getName()).isEqualTo("새이름");
      assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
      then(userRepository).should().findByIdAndDeletedAtIsNull(userId);
      then(userMapper).should().toDto(user);
    }

    @Test
    @DisplayName("다른 사용자의 프로필을 수정하면 UserForbiddenException이 발생한다")
    void fail_shouldThrowUserForbiddenException_whenRequesterIsNotOwner() {
      UUID requesterId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");

      assertThrows(
          UserForbiddenException.class,
          () -> userService.updateProfile(requesterId, userId, request, null));

      then(userRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않거나 삭제된 사용자의 프로필을 수정하면 UserNotFoundException이 발생한다")
    void fail_shouldThrowUserNotFoundException_whenUserDoesNotExist() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

      assertThrows(
          UserNotFoundException.class,
          () -> userService.updateProfile(userId, userId, request, null));

      then(userRepository).should().findByIdAndDeletedAtIsNull(userId);
    }
  }
}
