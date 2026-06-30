package com.team02.mopl.domain.notification.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class NotificationNotFoundException extends NotificationException {

  public NotificationNotFoundException() {
    super(ErrorCode.NOTIFICATION_NOT_FOUND);
  }
}
