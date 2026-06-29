package com.team02.mopl.domain.dm.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class ConversationNotFoundException extends DirectMessageException {

  public ConversationNotFoundException() {
    super(ErrorCode.CONVERSATION_NOT_FOUND);
  }
}
