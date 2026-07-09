package com.team02.mopl.domain.user.exception;

import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.global.exception.ErrorCode;

public class UserInvalidCursorException extends UserException {

  public UserInvalidCursorException(UserSortBy sortBy, String cursor, Throwable cause) {
    super(ErrorCode.INVALID_CURSOR, cause);
    addDetail("cursor", "'" + cursor + "' 은(는) " + sortBy.name() + " 정렬 기준의 올바른 커서 형식이 아닙니다.");
  }
}
