package com.team02.mopl.domain.review.exception;

import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class ReviewInvalidCursorException extends BusinessException {

  public ReviewInvalidCursorException(ReviewSortBy sortBy, String cursor) {
    super(ErrorCode.INVALID_CURSOR);
    addDetail("cursor", "'" + cursor + "' 은(는) " + sortBy.name() + " 정렬 기준의 올바른 커서 형식이 아닙니다.");
  }
}
