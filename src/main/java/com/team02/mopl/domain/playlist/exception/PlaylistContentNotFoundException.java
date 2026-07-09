package com.team02.mopl.domain.playlist.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class PlaylistContentNotFoundException extends PlaylistException {

  public PlaylistContentNotFoundException() {
    super(ErrorCode.PLAYLIST_CONTENT_NOT_FOUND);
  }
}
