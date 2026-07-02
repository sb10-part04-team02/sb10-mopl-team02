package com.team02.mopl.domain.user.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class UserForbiddenException extends UserException {

  public UserForbiddenException() {
    super(ErrorCode.FORBIDDEN);
  }
}
