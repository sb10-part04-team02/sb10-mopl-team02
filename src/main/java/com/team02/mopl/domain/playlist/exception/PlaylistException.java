package com.team02.mopl.domain.playlist.exception;

import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;

public class PlaylistException extends BusinessException {

  public PlaylistException(ErrorCode errorCode) {
    super(errorCode);
  }

  public PlaylistException(ErrorCode errorCode, Throwable cause) {
    super(errorCode, cause);
  }
}
