package com.team02.mopl.domain.subscription.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class SubscriptionNotFoundException extends SubscriptionException {

  public SubscriptionNotFoundException() {
    super(ErrorCode.SUBSCRIPTION_NOT_FOUND);
  }
}
