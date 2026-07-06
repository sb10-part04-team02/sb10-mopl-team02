package com.team02.mopl.domain.watching.util;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.time.format.DateTimeParseException;

public final class WatchingSessionCursorConverter {

  private WatchingSessionCursorConverter() {} // 인스턴스 생성 금지

  // 커서 문자열 -> 정렬 키 값. cursor가 없으면(첫 페이지) null 반환
  public static Instant toSortKey(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, e);
    }
  }
}
