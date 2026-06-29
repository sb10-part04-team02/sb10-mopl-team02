package com.team02.mopl.domain.notification.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class NotificationForbiddenException extends NotificationException {

  public NotificationForbiddenException() {
    super(ErrorCode.NOTIFICATION_FORBIDDEN);
  }
}
