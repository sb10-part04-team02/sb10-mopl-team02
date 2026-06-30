package com.team02.mopl.domain.notification.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class NotificationException extends BusinessException {

  public NotificationException(ErrorCode errorCode) {
    super(errorCode);
  }
}
