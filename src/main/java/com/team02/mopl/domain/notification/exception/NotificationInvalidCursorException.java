package com.team02.mopl.domain.notification.exception;

import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.global.exception.ErrorCode;

public class NotificationInvalidCursorException extends NotificationException {

  public NotificationInvalidCursorException(NotificationSortBy sortBy, String cursor) {
    super(ErrorCode.INVALID_CURSOR);
    addDetail("cursor", "'" + cursor + "' 은(는) " + sortBy.name() + " 정렬 기준의 올바른 커서 형식이 아닙니다.");
  }
}
