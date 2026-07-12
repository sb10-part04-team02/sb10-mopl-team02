package com.team02.mopl.global.exception;

// 커서 문자열이 정렬 기준 타입으로 파싱되지 않을 때 던지는 공통 예외.
// sortBy 는 도메인마다 enum 이 다르므로 String(호출부에서 sortBy.name())으로 받는다.
public class InvalidCursorException extends BusinessException {

  public InvalidCursorException(String sortBy, String cursor, Throwable cause) {
    super(ErrorCode.INVALID_CURSOR, cause);
    addDetail("cursor", "'" + cursor + "' 은(는) " + sortBy + " 정렬 기준의 올바른 커서 형식이 아닙니다.");
  }
}
