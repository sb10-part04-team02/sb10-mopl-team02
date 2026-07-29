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

  public BusinessException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }

  protected void addDetail(String key, String value) {
    if (key != null && value != null) {
      this.details.put(key, value);
    }
  }

  public Map<String, String> getDetails() {
    return Collections.unmodifiableMap(this.details);
  }
}
