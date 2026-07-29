package com.team02.mopl.domain.dm.util;

import com.team02.mopl.domain.dm.enums.DirectMessageSortBy;
import com.team02.mopl.global.exception.InvalidCursorException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

// 커서 문자열을 DM 정렬 키 값(Instant)으로 변환하는 유틸.
// 외부 입력(커서) 파싱·검증을 서비스 계층에서 처리하기 위해 분리했다.
public final class DirectMessageCursorConverter {

  private DirectMessageCursorConverter() {} // 인스턴스 생성 금지

  // 커서 문자열 -> 정렬 키 값(Instant)
  public static Instant toSortKey(DirectMessageSortBy sortBy, String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return switch (sortBy) {
        case CREATED_AT -> Instant.parse(cursor);
      };
    } catch (DateTimeParseException e) {
      throw new InvalidCursorException(sortBy.name(), cursor, e);
    }
  }
}
