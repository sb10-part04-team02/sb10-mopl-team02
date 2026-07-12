package com.team02.mopl.domain.content.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class InvalidCursorRequestException extends ContentException {

  public InvalidCursorRequestException() {
    super(ErrorCode.INVALID_CURSOR_REQUEST);
    addDetail("cursor", "cursor와 idAfter는 함께 전달되어야 합니다.");
  }
}
