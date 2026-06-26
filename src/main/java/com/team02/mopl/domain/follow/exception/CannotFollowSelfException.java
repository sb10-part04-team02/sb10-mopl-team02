package com.team02.mopl.domain.follow.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class CannotFollowSelfException extends FollowException {

  public CannotFollowSelfException() {
    super(ErrorCode.CANNOT_FOLLOW_SELF);
  }
}
