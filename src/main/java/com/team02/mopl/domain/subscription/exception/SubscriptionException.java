package com.team02.mopl.domain.subscription.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class SubscriptionException extends BusinessException {

  public SubscriptionException(ErrorCode errorCode) {
    super(errorCode);
  }
}
