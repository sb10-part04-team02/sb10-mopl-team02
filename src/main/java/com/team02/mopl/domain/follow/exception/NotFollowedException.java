package com.team02.mopl.domain.follow.exception;

import com.team02.mopl.global.exception.ErrorCode;
import java.util.UUID;

public class NotFollowedException extends FollowException {

  public NotFollowedException(UUID followeeId, UUID requesterId) {
    super(ErrorCode.FOLLOW_NOT_FOUND);
    addDetail("followeeId", followeeId.toString());
    addDetail("requesterId", requesterId.toString());
  }
}
