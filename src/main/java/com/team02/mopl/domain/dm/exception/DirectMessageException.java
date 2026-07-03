package com.team02.mopl.domain.dm.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class DirectMessageException extends BusinessException {

  public DirectMessageException(ErrorCode errorCode) {
    super(errorCode);
  }

  public DirectMessageException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
