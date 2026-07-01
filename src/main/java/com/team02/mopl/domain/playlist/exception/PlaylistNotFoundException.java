package com.team02.mopl.domain.playlist.exception;

import com.team02.mopl.global.exception.ErrorCode;

public class PlaylistNotFoundException extends PlaylistException {

  public PlaylistNotFoundException() {
    super(ErrorCode.PLAYLIST_NOT_FOUND);
  }
}
