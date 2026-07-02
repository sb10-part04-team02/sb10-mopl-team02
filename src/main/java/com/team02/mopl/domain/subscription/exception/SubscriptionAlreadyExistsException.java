package com.team02.mopl.domain.subscription.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class SubscriptionAlreadyExistsException extends SubscriptionException {

  public SubscriptionAlreadyExistsException() {
    super(ErrorCode.SUBSCRIPTION_ALREADY_EXISTS);
  }
}
