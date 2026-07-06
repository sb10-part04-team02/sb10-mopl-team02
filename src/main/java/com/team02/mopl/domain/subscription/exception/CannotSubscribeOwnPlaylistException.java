package com.team02.mopl.domain.subscription.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class CannotSubscribeOwnPlaylistException extends SubscriptionException {

  public CannotSubscribeOwnPlaylistException() {
    super(ErrorCode.CANNOT_SUBSCRIBE_OWN_PLAYLIST);
  }
}
