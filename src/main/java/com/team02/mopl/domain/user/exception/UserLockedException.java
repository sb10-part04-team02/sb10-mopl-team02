package com.team02.mopl.domain.user.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class UserLockedException extends UserException {

  public UserLockedException() {
    super(ErrorCode.USER_LOCKED);
  }
}
