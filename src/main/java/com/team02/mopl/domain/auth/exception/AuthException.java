package com.team02.mopl.domain.auth.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class AuthException extends RuntimeException {

  public AuthException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
  }
}
