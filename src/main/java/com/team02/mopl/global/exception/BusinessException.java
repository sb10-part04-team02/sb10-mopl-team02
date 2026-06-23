package com.team02.mopl.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String details;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.details = errorCode.getMessage();
  }

  public BusinessException(ErrorCode errorCode, String details) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.details = details;
  }
}
