package com.team02.mopl.domain.auth.exception;

import com.team02.mopl.global.exception.ErrorCode;
import lombok.Getter;

@Getter
public class AuthException extends RuntimeException {

  private final ErrorCode errorCode;

  // AuthException은 특수목적의 Exception임
  // 전부 에러코드 전부 500에 해당하고, 외부에 노출이 안되야 하면서 로그를 남겨야 함
  // GlobalExceptionHandler 구성에 따르면 BusinessException는 로그를 남기지 않아서
  // 예상외의 error로그를 남기는 Exception을 매칭하고자 RuntimeException을 상속받음
  public AuthException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
  }

  public AuthException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
  }
}
