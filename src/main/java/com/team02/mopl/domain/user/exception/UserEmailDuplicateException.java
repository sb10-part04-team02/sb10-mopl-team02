package com.team02.mopl.domain.user.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class UserEmailDuplicateException extends UserException {

  public UserEmailDuplicateException() {
    super(ErrorCode.EMAIL_DUPLICATED);
  }
}
