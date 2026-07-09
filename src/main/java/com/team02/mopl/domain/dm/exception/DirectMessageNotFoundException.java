package com.team02.mopl.domain.dm.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class DirectMessageNotFoundException extends DirectMessageException {

  public DirectMessageNotFoundException() {
    super(ErrorCode.DIRECT_MESSAGE_NOT_FOUND);
  }
}
