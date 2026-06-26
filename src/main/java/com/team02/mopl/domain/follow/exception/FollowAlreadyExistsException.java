package com.team02.mopl.domain.follow.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class FollowAlreadyExistsException extends FollowException {

  public FollowAlreadyExistsException() {
    super(ErrorCode.FOLLOW_ALREADY_EXISTS);
  }
}
