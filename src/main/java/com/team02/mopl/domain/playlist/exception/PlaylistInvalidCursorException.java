package com.team02.mopl.domain.playlist.exception;

import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.global.exception.ErrorCode;

public class PlaylistInvalidCursorException extends PlaylistException {

  public PlaylistInvalidCursorException(PlaylistSortBy sortBy, String cursor, Throwable cause) {
    super(ErrorCode.INVALID_CURSOR, cause);
    addDetail("cursor", "'" + cursor + "' 은(는) " + sortBy.name() + " 정렬 기준의 올바른 커서 형식이 아닙니다.");
  }
}
