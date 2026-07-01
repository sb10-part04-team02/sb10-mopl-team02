package com.team02.mopl.domain.auth.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class CompromisedTokenException extends AuthException {

  public CompromisedTokenException() {
    super(ErrorCode.COMPROMISED_TOKEN);
  }
}
