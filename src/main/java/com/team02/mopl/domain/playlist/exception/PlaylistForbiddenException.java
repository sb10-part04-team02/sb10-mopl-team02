package com.team02.mopl.domain.playlist.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class PlaylistForbiddenException extends PlaylistException {

  public PlaylistForbiddenException() {
    super(ErrorCode.PLAYLIST_FORBIDDEN);
  }
}
