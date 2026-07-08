package com.team02.mopl.domain.content.ingestion.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

// 외부 API 호출 실패 공통 예외
public class ExternalApiException extends BusinessException {

  public ExternalApiException(ErrorCode errorCode) {
    super(errorCode);
  }

  public ExternalApiException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
