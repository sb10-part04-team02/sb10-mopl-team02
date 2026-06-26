package com.team02.mopl.domain.follow.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class FollowNotFoundException extends FollowException {

  public FollowNotFoundException() {
    super(ErrorCode.FOLLOW_NOT_FOUND);
  }
}
