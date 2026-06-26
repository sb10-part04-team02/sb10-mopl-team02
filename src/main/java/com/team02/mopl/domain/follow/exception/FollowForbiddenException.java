package com.team02.mopl.domain.follow.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class FollowForbiddenException extends FollowException {

  public FollowForbiddenException() {
    super(ErrorCode.FOLLOW_FORBIDDEN);
  }
}
