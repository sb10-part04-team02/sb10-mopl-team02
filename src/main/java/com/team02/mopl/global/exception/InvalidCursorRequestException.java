package com.team02.mopl.global.exception;

// cursor 와 idAfter 조합이 올바르지 않을 때(한쪽만 전달) 던지는 공통 예외.
public class InvalidCursorRequestException extends BusinessException {

  public InvalidCursorRequestException() {
    super(ErrorCode.INVALID_CURSOR_REQUEST);
    addDetail("cursor", "cursor와 idAfter는 함께 전달되어야 합니다.");
  }
}
