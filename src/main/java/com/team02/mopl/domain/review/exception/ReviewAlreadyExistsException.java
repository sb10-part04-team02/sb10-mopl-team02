package com.team02.mopl.domain.review.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class ReviewAlreadyExistsException extends BusinessException {

  public ReviewAlreadyExistsException() {
    super(ErrorCode.REVIEW_ALREADY_EXISTS);
  }
}
