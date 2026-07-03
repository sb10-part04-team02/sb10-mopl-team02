package com.team02.mopl.domain.dm.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class DmInvalidCursorException extends DirectMessageException {

  public DmInvalidCursorException(String sortBy, String cursor) {
    super(ErrorCode.INVALID_CURSOR);
    addDetail("cursor", "'" + cursor + "' 은(는) " + sortBy + " 정렬 기준의 올바른 커서 형식이 아닙니다.");
  }
}
