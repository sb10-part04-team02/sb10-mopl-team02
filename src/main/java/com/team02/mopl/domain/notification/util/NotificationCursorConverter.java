package com.team02.mopl.domain.notification.util;

import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.domain.notification.exception.NotificationInvalidCursorException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

// 커서 문자열을 알림 정렬 키 값(Instant)으로 변환하는 유틸.
// 외부 입력(커서) 파싱·검증을 서비스 계층에서 처리하기 위해 분리했다.
public final class NotificationCursorConverter {

  private NotificationCursorConverter() {} // 인스턴스 생성 금지

  // 커서 문자열 -> 정렬 키 값(Instant)
  public static Instant toSortKey(NotificationSortBy sortBy, String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return switch (sortBy) {
        case createdAt -> Instant.parse(cursor);
      };
    } catch (DateTimeParseException e) {
      throw new NotificationInvalidCursorException(sortBy, cursor, e);
    }
  }
}
