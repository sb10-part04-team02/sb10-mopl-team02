package com.team02.mopl.domain.user.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class UserNotFoundException extends UserException {

  public UserNotFoundException() {
    super(ErrorCode.USER_NOT_FOUND);
  }
}
