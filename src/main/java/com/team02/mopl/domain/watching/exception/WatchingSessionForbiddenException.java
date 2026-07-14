package com.team02.mopl.domain.watching.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class WatchingSessionForbiddenException extends WatchingException {

  public WatchingSessionForbiddenException() {
    super(ErrorCode.FORBIDDEN);
  }
}
