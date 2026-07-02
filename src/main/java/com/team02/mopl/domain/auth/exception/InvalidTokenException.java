package com.team02.mopl.domain.auth.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class InvalidTokenException extends AuthException {

  public InvalidTokenException() {
    super(ErrorCode.INVALID_TOKEN);
  }
}
