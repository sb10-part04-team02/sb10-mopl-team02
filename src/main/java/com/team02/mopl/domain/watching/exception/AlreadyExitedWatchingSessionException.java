package com.team02.mopl.domain.watching.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class AlreadyExitedWatchingSessionException extends WatchingException {

  public AlreadyExitedWatchingSessionException() {
    super(ErrorCode.WATCHING_SESSION_ALREADY_EXITED);
  }
}
