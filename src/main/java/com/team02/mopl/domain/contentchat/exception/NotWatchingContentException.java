package com.team02.mopl.domain.contentchat.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class NotWatchingContentException extends BusinessException {

  public NotWatchingContentException() {
    super(ErrorCode.CONTENT_CHAT_NOT_WATCHING);
  }
}
