package com.team02.mopl.domain.user.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.exception.UserInvalidCursorException;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserCursorConverterTest {

  @Nested
  class ToSortKey {

    private static Stream<Arguments> provideValidCursor() {
      return Stream.of(
          Arguments.of(UserSortBy.NAME, String.class, "이름"),
          Arguments.of(UserSortBy.EMAIL, String.class, "example@gmail.com"),
          Arguments.of(UserSortBy.CREATED_AT, Instant.class, Instant.now().toString()),
          Arguments.of(UserSortBy.IS_LOCKED, Boolean.class, "false"),
          Arguments.of(UserSortBy.IS_LOCKED, Boolean.class, "true"),
          Arguments.of(UserSortBy.ROLE, Role.class, Role.USER.name()));
    }

    @ParameterizedTest
    @MethodSource("provideValidCursor")
    @DisplayName("정렬기준과 cursor문자열이 들어오면 정렬 키값으로 변환한다")
    void success_shouldTransformToSortKey_whenSortByAndCursorIsProvided(
        UserSortBy sortBy, Class<?> type, String cursor) {
      // when
      Comparable<?> actual = UserCursorConverter.toSortKey(sortBy, cursor);

      // then
      assertThat(actual).isInstanceOf(type);
    }

    private static Stream<Arguments> provideNullableCursor() {
      return Stream.of(
          Arguments.of(UserSortBy.NAME, null, "cursor 누락"),
          Arguments.of(UserSortBy.NAME, "  ", "빈 cursor"));
    }

    @ParameterizedTest
    @MethodSource("provideNullableCursor")
    @DisplayName("nullable한 cursor가 들어오면 null을 반환한다")
    void fail_shouldReturnNull_whenNullableCursorIsProvided(UserSortBy sortBy, String cursor) {
      // when
      Comparable<?> actual = UserCursorConverter.toSortKey(sortBy, cursor);

      // then
      assertThat(actual).isNull();
    }

    private static Stream<Arguments> provideInValidCursor() {
      return Stream.of(
          Arguments.of(UserSortBy.CREATED_AT, "invalid instant", "유효하지 않은 instant"),
          Arguments.of(UserSortBy.IS_LOCKED, "invalid boolean", "유효하지 않은 isLocked"),
          Arguments.of(UserSortBy.ROLE, "invalid Role", "유효하지않은 Role값"));
    }

    @ParameterizedTest
    @MethodSource("provideInValidCursor")
    @DisplayName("유효하지않은 cursor문자열이 들어오면 예외를 던진다")
    void fail_shouldThrowException_whenInvalidCursorIsProvided(UserSortBy sortBy, String cursor) {
      // when & then
      assertThrows(
          UserInvalidCursorException.class, () -> UserCursorConverter.toSortKey(sortBy, cursor));
    }
  }

  @Nested
  class ToCursor {
    @ParameterizedTest
    @EnumSource(UserSortBy.class)
    @DisplayName("정렬기준에 따라 다른 cursor값을 반환한다")
    void success_shouldReturnString_whenUserSortByIsProvided(UserSortBy sortBy) {
      // given
      String name = "이름";
      String email = "example@gmail.com";
      Instant createdAt = Instant.now();
      boolean isLocked = false;
      Role role = Role.ADMIN;

      String expect =
          switch (sortBy) {
            case NAME -> name;
            case EMAIL -> email;
            case CREATED_AT -> createdAt.toString();
            case IS_LOCKED -> Boolean.toString(isLocked);
            case ROLE -> role.name();
          };

      User user = new User(name, email, "pwd", null, role, isLocked);
      ReflectionTestUtils.setField(user, "createdAt", createdAt);

      // when
      String actual = UserCursorConverter.toCursor(sortBy, user);

      // then
      assertThat(actual).isEqualTo(expect);
    }
  }
}
