package com.team02.mopl.domain.dm.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class ConversationForbiddenException extends DirectMessageException {

  public ConversationForbiddenException() {
    super(ErrorCode.FORBIDDEN);
  }
}
