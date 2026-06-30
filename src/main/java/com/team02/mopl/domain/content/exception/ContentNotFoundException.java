package com.team02.mopl.domain.content.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class ContentNotFoundException extends ContentException {

  public ContentNotFoundException() {
    super(ErrorCode.CONTENT_NOT_FOUND);
  }
}
