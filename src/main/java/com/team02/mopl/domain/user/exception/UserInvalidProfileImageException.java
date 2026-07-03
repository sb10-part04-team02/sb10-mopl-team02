package com.team02.mopl.domain.user.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class UserInvalidProfileImageException extends UserException {

  public UserInvalidProfileImageException() {
    super(ErrorCode.INVALID_REQUEST);
  }
}
