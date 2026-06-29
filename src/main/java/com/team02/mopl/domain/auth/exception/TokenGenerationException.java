package com.team02.mopl.domain.auth.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class TokenGenerationException extends AuthException {

  public TokenGenerationException(Throwable cause) {
    super(ErrorCode.INTERNAL_SERVER_ERROR, cause);
  }
}
