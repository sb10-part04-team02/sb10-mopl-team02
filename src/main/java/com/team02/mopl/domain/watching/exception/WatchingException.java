package com.team02.mopl.domain.watching.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class WatchingException extends BusinessException {

  public WatchingException(ErrorCode errorCode) {
    super(errorCode);
  }

  public WatchingException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
