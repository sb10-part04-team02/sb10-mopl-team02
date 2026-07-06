package com.team02.mopl.domain.playlist.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class PlaylistContentAlreadyExistsException extends PlaylistException {

  public PlaylistContentAlreadyExistsException() {
    super(ErrorCode.PLAYLIST_CONTENT_ALREADY_EXISTS);
  }
}
