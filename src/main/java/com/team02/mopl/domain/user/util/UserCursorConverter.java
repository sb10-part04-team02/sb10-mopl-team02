package com.team02.mopl.domain.user.util;

import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.domain.user.exception.UserInvalidCursorException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class UserCursorConverter {

  private UserCursorConverter() {} // 인스턴스 생성 금지

  // 커서 문자열 -> 정렬 키 값
  public static Comparable<?> toSortKey(UserSortBy sortBy, String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return switch (sortBy) {
        case NAME, EMAIL -> cursor;
        case CREATED_AT -> Instant.parse(cursor);
        case IS_LOCKED -> parseBooleanCursor(cursor);
        case ROLE -> Role.valueOf(cursor);
      };
    } catch (DateTimeParseException | IllegalArgumentException e) {
      throw new UserInvalidCursorException(sortBy, cursor, e);
    }
  }

  // 마지막 행의 정렬 키 값 -> 다음 커서 문자열
  public static String toCursor(UserSortBy sortBy, User last) {
    return switch (sortBy) {
      case NAME -> last.getName();
      case EMAIL -> last.getEmail();
      case CREATED_AT -> last.getCreatedAt().toString();
      case IS_LOCKED -> Boolean.toString(last.isLocked());
      case ROLE -> last.getRole().name();
    };
  }

  private static Boolean parseBooleanCursor(String cursor) {
    if (!"true".equalsIgnoreCase(cursor) && !"false".equalsIgnoreCase(cursor)) {
      throw new IllegalArgumentException();
    }
    return Boolean.parseBoolean(cursor);
  }
}
