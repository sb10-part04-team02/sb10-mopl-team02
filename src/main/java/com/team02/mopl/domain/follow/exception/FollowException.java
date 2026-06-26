package com.team02.mopl.domain.follow.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class FollowException extends BusinessException {

  public FollowException(ErrorCode errorCode) {
    super(errorCode);
  }
}
