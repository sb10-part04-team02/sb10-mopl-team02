package com.team02.mopl.global.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final Map<String, String> details = new HashMap<>();

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String details) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public Map<String, String> getDetails() {
    return Collections.unmodifiableMap(this.details);
  }
}
