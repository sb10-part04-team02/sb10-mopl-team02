package com.team02.mopl.domain.dm.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class ConversationAlreadyExistsException extends DirectMessageException {

  public ConversationAlreadyExistsException() {
    super(ErrorCode.CONVERSATION_ALREADY_EXISTS);
  }
}
