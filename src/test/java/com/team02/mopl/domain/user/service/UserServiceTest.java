package com.team02.mopl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.oauth.provider.OAuth2UserInfo;
import com.team02.mopl.domain.auth.oauth.provider.OAuthType;
import com.team02.mopl.domain.user.dto.ChangePasswordRequest;
import com.team02.mopl.domain.user.dto.UserCreateRequest;
import com.team02.mopl.domain.user.dto.UserDto;
import com.team02.mopl.domain.user.dto.UserLockUpdateRequest;
import com.team02.mopl.domain.user.dto.UserRoleUpdateRequest;
import com.team02.mopl.domain.user.dto.UserSearchRequest;
import com.team02.mopl.domain.user.dto.UserUpdateRequest;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.event.PasswordUpdatedEvent;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import com.team02.mopl.domain.user.event.UserLockUpdatedEvent;
import com.team02.mopl.domain.user.exception.UserEmailDuplicateException;
import com.team02.mopl.domain.user.exception.UserForbiddenException;
import com.team02.mopl.domain.user.exception.UserInvalidProfileImageException;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.mapper.UserMapper;
import com.team02.mopl.domain.user.repository.SocialAccountRepository;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.storage.FileStorage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock UserRepository userRepository;

  @Mock UserMapper userMapper;

  @Mock ApplicationEventPublisher eventPublisher;

  @Mock FileStorage fileStorage;

  @Mock SocialAccountRepository socialAccountRepository;

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
      assertThrows(
          UserEmailDuplicateException.class,
          () -> userService.createUser(request, any(Role.class)));
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
      assertThrows(
          UserEmailDuplicateException.class, () -> userService.createUser(request, Role.USER));
    }

    @Test
    @DisplayName("정상적인 회원가입 요청일 때 UserDto를 반환한다")
    void success_shouldReturnUserDto_whenRequestIsValid() {
      // given
      UserCreateRequest request = new UserCreateRequest(name, email, password);
      Role userRole = Role.USER;
      User mockUser = new User(name, email, "encryptedPassword", null, userRole, false);
      UserDto expect =
          new UserDto(UUID.randomUUID(), Instant.now(), email, name, null, userRole, false);
      given(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).willReturn(false);
      given(userRepository.saveAndFlush(any(User.class))).willReturn(mockUser);
      given(userMapper.toDto(any(User.class))).willReturn(expect);

      // when
      UserDto actual = userService.createUser(request, userRole);

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
      Role userRole = Role.USER;
      UserCreateRequest request = new UserCreateRequest(name, shortEmail, password);
      User mockUser = new User(name, shortEmail, "encryptedPassword", null, userRole, false);
      UserDto expect =
          new UserDto(UUID.randomUUID(), Instant.now(), shortEmail, name, null, userRole, false);
      given(userRepository.existsByEmailAndDeletedAtIsNull(anyString())).willReturn(false);
      given(userRepository.saveAndFlush(any(User.class))).willReturn(mockUser);
      given(userMapper.toDto(any(User.class))).willReturn(expect);

      // when
      UserDto actual = userService.createUser(request, userRole);

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
      Integer limit = 3;
      long totalCount = 2L;
      UserSearchRequest request =
          new UserSearchRequest(
              null, null, null, null, null, limit, SortDirection.ASCENDING, UserSortBy.NAME);

      List<User> users = createBasicUserList();
      given(
              userRepository.findUsersByCursor(
                  any(), any(), any(), any(), any(), anyInt(), any(), any()))
          .willReturn(users);
      given(userMapper.toDto(any(User.class))).willReturn(mock(UserDto.class));
      given(
              userRepository.countUsersByCursor(
                  request.emailLike(), request.roleEqual(), request.isLocked()))
          .willReturn(totalCount);

      // when
      CursorResponse<UserDto> actual = userService.getUsers(request);

      // then
      assertThat(actual)
          .isNotNull()
          .satisfies(
              act -> {
                assertThat(act.data()).hasSize(users.size());
                assertThat(act.hasNext()).isFalse();
                assertThat(act.totalCount()).isEqualTo(totalCount);
                assertThat(act.sortBy()).isEqualTo(UserSortBy.NAME.getValue());
                assertThat(act.sortDirection()).isEqualTo(SortDirection.ASCENDING.name());
              });
    }

    private List<User> createBasicUserList() {
      User user1 = new User("이름", "example1@gmail.com", "password1", null, Role.USER, false);
      User user2 = new User("이름2", "example2@apple.com", "password2", null, Role.ADMIN, false);
      return List.of(user1, user2);
    }

    @Test
    @DisplayName("검색 조건에 맞는 유저가 없으면 빈 data와 null 커서를 반환한다")
    void success_shouldReturnEmptyDataAndNullCursor_whenNoUsersFound() {
      // given
      UserSearchRequest request =
          new UserSearchRequest(
              "nobody-example@gmail.com",
              null,
              null,
              null,
              null,
              10,
              SortDirection.ASCENDING,
              UserSortBy.NAME);

      given(
              userRepository.findUsersByCursor(
                  any(), any(), any(), any(), any(), anyInt(), any(), any()))
          .willReturn(List.of());
      given(
              userRepository.countUsersByCursor(
                  request.emailLike(), request.roleEqual(), request.isLocked()))
          .willReturn(0L);

      // when
      CursorResponse<UserDto> actual = userService.getUsers(request);

      // then
      assertThat(actual)
          .isNotNull()
          .satisfies(
              act -> {
                assertThat(act.data()).isEmpty();
                assertThat(act.nextCursor()).isNull();
                assertThat(act.nextIdAfter()).isNull();
                assertThat(act.hasNext()).isFalse();
                assertThat(act.totalCount()).isZero();
                assertThat(act.sortBy()).isEqualTo(UserSortBy.NAME.getValue());
                assertThat(act.sortDirection()).isEqualTo(SortDirection.ASCENDING.name());
              });
    }

    @Test
    @DisplayName("데이터가 limit보다 많으면 hasNext가 true가 되고 다음 커서정보를 반환한다")
    void success_shouldReturnHasNextTrue_whenMoreDataExists() {
      // given
      Integer limit = 2;
      UserSearchRequest request =
          new UserSearchRequest(
              null, null, null, null, null, limit, SortDirection.ASCENDING, UserSortBy.NAME);

      User user1 = mock(User.class);
      User user2 = mock(User.class);
      User user3 = mock(User.class);
      List<User> users = List.of(user1, user2, user3);

      // limit으로 마지막 되는 항목은 user2
      given(user2.getName()).willReturn("이름");
      given(user2.getId()).willReturn(UUID.randomUUID());

      given(
              userRepository.findUsersByCursor(
                  any(), any(), any(), any(), any(), eq(limit + 1), any(), any()))
          .willReturn(users);
      given(userMapper.toDto(any(User.class))).willReturn(mock(UserDto.class));

      long totalCount = 10L;
      given(
              userRepository.countUsersByCursor(
                  request.emailLike(), request.roleEqual(), request.isLocked()))
          .willReturn(totalCount);

      // when
      CursorResponse<UserDto> actual = userService.getUsers(request);

      // then
      assertThat(actual)
          .isNotNull()
          .satisfies(
              act -> {
                assertThat(act.data()).hasSize(limit);
                assertThat(act.nextCursor()).isNotNull();
                assertThat(act.nextIdAfter()).isNotNull();
                assertThat(act.hasNext()).isTrue();
                assertThat(act.totalCount()).isEqualTo(totalCount);
                assertThat(act.sortBy()).isEqualTo(request.sortBy().getValue());
                assertThat(act.sortDirection()).isEqualTo(request.sortDirection().name());
              });
    }

    @Test
    @DisplayName("필수 파라미터가 누락되면 기본값을 적용해서 조회한다")
    void success_shouldApplyDefaultValues_whenRequiredFieldsAreNull() {
      // given
      // 필드별 디폴트 값. limit: 20, sortDirection: ASCENDING, sortBy: name
      UserSearchRequest request =
          new UserSearchRequest(null, null, null, null, null, null, null, null);

      List<User> users = createBasicUserList();
      given(
              userRepository.findUsersByCursor(
                  any(), any(), any(), any(), any(), anyInt(), any(), any()))
          .willReturn(users);
      given(userMapper.toDto(any(User.class))).willReturn(mock(UserDto.class));
      given(
              userRepository.countUsersByCursor(
                  request.emailLike(), request.roleEqual(), request.isLocked()))
          .willReturn(10L);

      // when
      CursorResponse<UserDto> actual = userService.getUsers(request);

      // then
      ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
      then(userRepository)
          .should()
          .findUsersByCursor(
              any(), any(), any(), any(), any(), limitCaptor.capture(), any(), any());

      // 기본값 20. +1은 요청시 +1 다음 항목 있는지 확인하기 위한 것
      assertThat(limitCaptor.getValue()).isEqualTo(20 + 1);

      assertThat(actual)
          .isNotNull()
          .satisfies(
              act -> {
                assertThat(actual.sortBy()).isEqualTo(UserSortBy.NAME.getValue());
                assertThat(actual.sortDirection()).isEqualTo(SortDirection.ASCENDING.name());
              });
    }

    @ParameterizedTest
    @EnumSource(UserSortBy.class)
    @DisplayName("각 정렬이 주어지면 대응하는 필드값으로 커서를 올바르게 반환한다")
    void success_shouldEncodeCursorCorrectly_whenEachSortByOptionIsProvided(UserSortBy sortBy) {
      // given
      UserSearchRequest request =
          new UserSearchRequest(null, null, null, null, null, 1, SortDirection.ASCENDING, sortBy);

      User user = new User("이름", "example@gmail.com", "pwd", null, Role.USER, false);
      ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(user, "createdAt", Instant.now());

      given(
              userRepository.findUsersByCursor(
                  any(), any(), any(), any(), any(), anyInt(), any(), any()))
          .willReturn(List.of(user, user));
      given(userMapper.toDto(any(User.class))).willReturn(mock(UserDto.class));

      // when
      CursorResponse<UserDto> actual = userService.getUsers(request);

      // then
      String expectedCursor =
          switch (sortBy) {
            case NAME -> user.getName();
            case EMAIL -> user.getEmail();
            case CREATED_AT -> user.getCreatedAt().toString();
            case IS_LOCKED -> Boolean.toString(user.isLocked());
            case ROLE -> user.getRole().name();
          };

      assertThat(actual.nextCursor()).isEqualTo(expectedCursor);
    }

    private static Stream<Arguments> provideCursorOrIdAfterMissing() {
      return Stream.of(
          Arguments.of(null, UUID.randomUUID(), "cursor 누락"),
          Arguments.of("cursor", null, "idAfter 누락"));
    }

    @ParameterizedTest
    @MethodSource("provideCursorOrIdAfterMissing")
    @DisplayName("cursor나 idAfter 둘 중 하나만 있으면 예외를 던진다")
    void fail_shouldThrowException_whenCursorOrIdAfterIsMissing(String cursor, UUID idAfter) {
      //
      UserSearchRequest request =
          new UserSearchRequest(
              null, null, null, cursor, idAfter, 10, SortDirection.ASCENDING, UserSortBy.NAME);

      // when & then
      assertThrows(BusinessException.class, () -> userService.getUsers(request));
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
      then(fileStorage).should(never()).store(any());
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

    @Test
    @DisplayName("프로필 이미지가 있으면 파일 저장 후 반환된 URL로 프로필 이미지를 변경한다")
    void success_shouldUpdateProfileImageUrl_whenImageExists() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      MultipartFile image = mock(MultipartFile.class);
      User user =
          new User(
              "기존이름",
              "woody@mopl.io",
              "password",
              "https://example.com/old-profile.png",
              Role.USER,
              false);
      UserDto expect =
          new UserDto(
              userId,
              Instant.parse("2026-07-02T00:00:00Z"),
              "woody@mopl.io",
              "새이름",
              "https://example.com/new-profile.png",
              Role.USER,
              false);

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
      given(image.isEmpty()).willReturn(false);
      given(image.getSize()).willReturn(1024L);
      given(image.getContentType()).willReturn("image/png");
      given(fileStorage.store(image)).willReturn("https://example.com/new-profile.png");
      given(userMapper.toDto(user)).willReturn(expect);

      UserDto actual = userService.updateProfile(userId, userId, request, image);

      assertThat(actual).isEqualTo(expect);
      assertThat(user.getName()).isEqualTo("새이름");
      assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new-profile.png");
      then(fileStorage).should().store(image);
      then(userMapper).should().toDto(user);
    }

    @Test
    @DisplayName("프로필 이미지가 빈 파일이면 저장하지 않고 기존 프로필 이미지 URL을 유지한다")
    void success_shouldKeepExistingProfileImageUrl_whenImageIsEmpty() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      MultipartFile image = mock(MultipartFile.class);
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
      given(image.isEmpty()).willReturn(true);
      given(userMapper.toDto(user)).willReturn(expect);

      UserDto actual = userService.updateProfile(userId, userId, request, image);

      assertThat(actual).isEqualTo(expect);
      assertThat(user.getName()).isEqualTo("새이름");
      assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
      then(fileStorage).should(never()).store(any());
      then(userMapper).should().toDto(user);
    }

    @Test
    @DisplayName("프로필 이미지 Content-Type이 허용되지 않으면 UserInvalidProfileImageException이 발생한다")
    void fail_shouldThrowUserInvalidProfileImageException_whenContentTypeIsNotAllowed() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      MultipartFile image = mock(MultipartFile.class);
      User user =
          new User(
              "기존이름",
              "woody@mopl.io",
              "password",
              "https://example.com/profile.png",
              Role.USER,
              false);

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
      given(image.isEmpty()).willReturn(false);
      given(image.getSize()).willReturn(1024L);
      given(image.getContentType()).willReturn("application/pdf");

      assertThrows(
          UserInvalidProfileImageException.class,
          () -> userService.updateProfile(userId, userId, request, image));

      then(fileStorage).should(never()).store(any());
    }

    @Test
    @DisplayName("프로필 이미지 크기가 제한을 초과하면 UserInvalidProfileImageException이 발생한다")
    void fail_shouldThrowUserInvalidProfileImageException_whenImageSizeExceeded() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      MultipartFile image = mock(MultipartFile.class);
      User user =
          new User(
              "기존이름",
              "woody@mopl.io",
              "password",
              "https://example.com/profile.png",
              Role.USER,
              false);

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
      given(image.isEmpty()).willReturn(false);
      given(image.getSize()).willReturn(5 * 1024 * 1024 + 1L);

      assertThrows(
          UserInvalidProfileImageException.class,
          () -> userService.updateProfile(userId, userId, request, image));

      then(fileStorage).should(never()).store(any());
    }

    @Test
    @DisplayName("프로필 이미지를 교체하면 기존 프로필 이미지를 삭제한다")
    void success_shouldDeleteOldProfileImage_whenProfileImageReplaced() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      MultipartFile image = mock(MultipartFile.class);

      User user =
          new User(
              "기존이름",
              "woody@mopl.io",
              "password",
              "https://example.com/old-profile.png",
              Role.USER,
              false);

      UserDto expect =
          new UserDto(
              userId,
              Instant.parse("2026-07-02T00:00:00Z"),
              "woody@mopl.io",
              "새이름",
              "https://example.com/new-profile.png",
              Role.USER,
              false);

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
      given(image.isEmpty()).willReturn(false);
      given(image.getSize()).willReturn(1024L);
      given(image.getContentType()).willReturn("image/png");
      given(fileStorage.store(image)).willReturn("https://example.com/new-profile.png");
      given(userMapper.toDto(user)).willReturn(expect);

      UserDto actual = userService.updateProfile(userId, userId, request, image);

      assertThat(actual).isEqualTo(expect);
      assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new-profile.png");
      then(fileStorage).should().store(image);
      then(fileStorage).should().delete("https://example.com/old-profile.png");
    }

    @Test
    @DisplayName("프로필 이미지가 빈 파일이면 기존 프로필 이미지를 삭제하지 않는다")
    void success_shouldNotDeleteOldProfileImage_whenImageIsEmpty() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      MultipartFile image = mock(MultipartFile.class);

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
      given(image.isEmpty()).willReturn(true);
      given(userMapper.toDto(user)).willReturn(expect);

      UserDto actual = userService.updateProfile(userId, userId, request, image);

      assertThat(actual).isEqualTo(expect);
      assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
      then(fileStorage).should(never()).store(any());
      then(fileStorage).should(never()).delete(any());
    }

    @Test
    @DisplayName("기존 프로필 이미지 삭제에 실패해도 프로필 수정은 성공한다")
    void success_shouldUpdateProfileEvenWhenOldProfileImageDeleteFails() {
      UUID userId = UUID.randomUUID();
      UserUpdateRequest request = new UserUpdateRequest("새이름");
      MultipartFile image = mock(MultipartFile.class);

      User user =
          new User(
              "기존이름",
              "woody@mopl.io",
              "password",
              "https://example.com/old-profile.png",
              Role.USER,
              false);

      UserDto expect =
          new UserDto(
              userId,
              Instant.parse("2026-07-02T00:00:00Z"),
              "woody@mopl.io",
              "새이름",
              "https://example.com/new-profile.png",
              Role.USER,
              false);

      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(user));
      given(image.isEmpty()).willReturn(false);
      given(image.getSize()).willReturn(1024L);
      given(image.getContentType()).willReturn("image/png");
      given(fileStorage.store(image)).willReturn("https://example.com/new-profile.png");
      willThrow(new RuntimeException("delete failed"))
          .given(fileStorage)
          .delete("https://example.com/old-profile.png");
      given(userMapper.toDto(user)).willReturn(expect);

      UserDto actual = userService.updateProfile(userId, userId, request, image);

      assertThat(actual).isEqualTo(expect);
      assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/new-profile.png");
      then(fileStorage).should().delete("https://example.com/old-profile.png");
    }
  }

  @Nested
  class UpdateRole {

    @Test
    @DisplayName("권한변경요청이 들어오면 권한을 변경한다")
    void success_shouldChangeRole_whenRoleUpdateRequestIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      User user = new User("이름", "example@gmail.com", "password", null, Role.USER, false);
      given(userRepository.findByIdAndDeletedAtIsNull(eq(userId))).willReturn(Optional.of(user));
      willDoNothing().given(eventPublisher).publishEvent(any(RoleUpdatedEvent.class));

      UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);

      // when
      userService.updateRole(userId, request);

      // then
      assertThat(user.getRole()).isEqualTo(Role.ADMIN);

      ArgumentCaptor<RoleUpdatedEvent> eventCaptor =
          ArgumentCaptor.forClass(RoleUpdatedEvent.class);
      then(eventPublisher).should().publishEvent(eventCaptor.capture());

      RoleUpdatedEvent event = eventCaptor.getValue();
      assertThat(event)
          .satisfies(
              e -> {
                assertThat(e.userId()).isEqualTo(userId);
                assertThat(e.oldRole()).isEqualTo(Role.USER);
                assertThat(e.newRole()).isEqualTo(Role.ADMIN);
              });
    }

    @Test
    @DisplayName("동일권한변경 요청이 들어오면 조기반환한다")
    void success_shouldReturnEarly_whenSameRoleIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      Role adminRole = Role.ADMIN;
      User user = new User("이름", "example@gmail.com", "password", null, adminRole, false);
      given(userRepository.findByIdAndDeletedAtIsNull(eq(userId))).willReturn(Optional.of(user));

      // 동일한 ADMIN 권한 변경요청
      UserRoleUpdateRequest request = new UserRoleUpdateRequest(adminRole);

      // when
      userService.updateRole(userId, request);

      // then
      then(eventPublisher).should(never()).publishEvent(any(RoleUpdatedEvent.class));
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 예외를 던진다")
    void fail_shouldThrowException_whenUserNotFound() {
      // given
      given(userRepository.findByIdAndDeletedAtIsNull(any(UUID.class)))
          .willReturn(Optional.empty());

      // when & then
      assertThrows(
          UserNotFoundException.class,
          () -> userService.updateRole(UUID.randomUUID(), mock(UserRoleUpdateRequest.class)));
      then(eventPublisher).should(never()).publishEvent(any(RoleUpdatedEvent.class));
    }
  }

  @Nested
  class UpdateLock {

    @Test
    @DisplayName("계정잠금변환요청이 들어오면 잠금상태를 변경하고 이벤트를 발행한다")
    void success_shouldChaneLockAndPublishEvent_whenLockUpdateRequestIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      User user = new User("이름", "example@gmail.com", "password", null, Role.USER, false);
      given(userRepository.findByIdAndDeletedAtIsNull(eq(userId))).willReturn(Optional.of(user));

      UserLockUpdateRequest request = new UserLockUpdateRequest(true);

      // when
      userService.updateLock(userId, request);

      // then
      assertThat(user.isLocked()).isEqualTo(true);
      then(eventPublisher).should(times(1)).publishEvent(new UserLockUpdatedEvent(userId, true));
    }

    @Test
    @DisplayName("동일잠금변경 요청이 들어오면 조기반환한다")
    void success_shouldReturnEarly_whenSameLockIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      boolean userLock = false;
      User user = new User("이름", "example@gmail.com", "password", null, Role.USER, userLock);

      // entity 메소드 실행여부 확인하기 위해 spy로 wrapping
      User spyUser = spy(user);
      given(userRepository.findByIdAndDeletedAtIsNull(eq(userId))).willReturn(Optional.of(spyUser));

      // 동일한 Lock 변경요청
      UserLockUpdateRequest request = new UserLockUpdateRequest(userLock);

      // when
      userService.updateLock(userId, request);

      // then
      then(spyUser).should(never()).updateLock(anyBoolean());
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 예외를 던진다")
    void fail_shouldThrowException_whenUserNotFound() {
      // given
      given(userRepository.findByIdAndDeletedAtIsNull(any(UUID.class)))
          .willReturn(Optional.empty());

      // when & then
      assertThrows(
          UserNotFoundException.class,
          () -> userService.updateLock(UUID.randomUUID(), mock(UserLockUpdateRequest.class)));
    }
  }

  @Nested
  class UpdatePassword {
    @Test
    @DisplayName("패스워드변경요청이 들어오면 패스워드를 변경한다")
    void success_shouldUpdatePassword_whenChangePasswordIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      User mockUser = mock(User.class);
      ChangePasswordRequest request = new ChangePasswordRequest("validPassword");
      given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(mockUser));

      String encodedPassword = "encodedPassword";
      given(passwordEncoder.encode(request.password())).willReturn(encodedPassword);

      given(mockUser.getId()).willReturn(userId);
      PasswordUpdatedEvent event = new PasswordUpdatedEvent(userId);

      // when
      userService.updatePassword(userId, userId, request);

      // then
      then(mockUser).should(times(1)).updatePassword(eq(encodedPassword));
      then(eventPublisher).should(times(1)).publishEvent(event);
    }

    @Test
    @DisplayName("유저가 존재하지 않으면 예외를 던진다")
    void fail_shouldThrowException_whenUserNotFound() {
      // given
      UUID userId = UUID.randomUUID();
      given(userRepository.findByIdAndDeletedAtIsNull(any(UUID.class)))
          .willReturn(Optional.empty());

      // when & then
      assertThrows(
          UserNotFoundException.class,
          () -> userService.updatePassword(userId, userId, mock(ChangePasswordRequest.class)));
      then(eventPublisher).should(never()).publishEvent(any(PasswordUpdatedEvent.class));
    }

    @Test
    @DisplayName("소유주가 아닐경우 예외를 던진다")
    void fail_shouldThrowException_whenUserAndRequesterAreNotEqual() {
      // given
      UUID userId = UUID.randomUUID();
      UUID requesterId = UUID.randomUUID();

      // when & then
      assertThrows(
          BusinessException.class,
          () -> userService.updatePassword(userId, requesterId, mock(ChangePasswordRequest.class)));
    }
  }

  @Nested
  class RegisterSocialUser {
    @Test
    @DisplayName("기존에 회원이었으면 등록과정을 스킵한다")
    void fail_shouldSkipRegisterUser_whenVisitorIsAlreadyUser() {
      // given
      OAuth2UserInfo info = mock(OAuth2UserInfo.class);
      given(info.email()).willReturn("example@gmail.com");
      given(userRepository.findByEmailAndDeletedAtIsNull(anyString()))
          .willReturn(Optional.of(mock(User.class)));
      given(socialAccountRepository.existsByUserIdAndProvider(any(), any())).willReturn(true);

      // when
      userService.registerSocialUser(info);

      // then
      then(socialAccountRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("처음들어오는 소셜회원이면 유저로 등록한다")
    void success_shouldRegisterUser_whenVisitorIsInit() {
      // given
      OAuth2UserInfo info =
          new OAuth2UserInfo(OAuthType.GOOGLE, "12345", "이름", "example@gmail.com", "url");
      given(userRepository.findByEmailAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());
      User mockUser = mock(User.class);
      given(mockUser.getId()).willReturn(UUID.randomUUID());
      given(userRepository.save(any())).willReturn(mockUser);

      // when
      userService.registerSocialUser(info);

      // then
      then(userRepository).should(times(1)).save(any());
      then(socialAccountRepository).should(times(1)).save(any());
    }
  }
}
