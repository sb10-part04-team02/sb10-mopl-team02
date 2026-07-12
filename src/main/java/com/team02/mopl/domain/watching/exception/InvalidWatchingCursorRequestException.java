package com.team02.mopl.domain.watching.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class InvalidWatchingCursorRequestException extends WatchingException {

  public InvalidWatchingCursorRequestException() {
    super(ErrorCode.INVALID_CURSOR_REQUEST);
    addDetail("cursor", "cursor와 idAfter는 함께 전달되어야 합니다.");
  }
}
