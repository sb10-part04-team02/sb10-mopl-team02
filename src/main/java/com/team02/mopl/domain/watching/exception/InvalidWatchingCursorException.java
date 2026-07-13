package com.team02.mopl.domain.watching.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class InvalidWatchingCursorException extends WatchingException {

  public InvalidWatchingCursorException(String cursor, Throwable cause) {
    super(ErrorCode.INVALID_CURSOR, cause);
    addDetail("cursor", "'" + cursor + "' 은(는) 올바른 커서 형식이 아닙니다.");
  }
}
