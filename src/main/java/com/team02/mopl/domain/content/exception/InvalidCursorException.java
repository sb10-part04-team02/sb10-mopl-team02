package com.team02.mopl.domain.content.exception;

import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.global.exception.ErrorCode;

public class InvalidCursorException extends ContentException {

  public InvalidCursorException(SortBy sortBy, String cursor) {
    super(ErrorCode.INVALID_CURSOR);
    addDetail("cursor", "'" + cursor + "' 은(는) " + sortBy.name() + " 정렬 기준의 올바른 커서 형식이 아닙니다.");
  }
}
