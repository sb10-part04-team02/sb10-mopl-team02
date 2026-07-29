package com.team02.mopl.domain.content.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class ContentException extends BusinessException {

  public ContentException(ErrorCode errorCode) {
    super(errorCode);
  }
}
