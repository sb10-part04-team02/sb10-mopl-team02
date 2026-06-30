package com.team02.mopl.domain.dm.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class SelfConversationException extends DirectMessageException {

  public SelfConversationException() {
    super(ErrorCode.SELF_CONVERSATION);
  }
}
