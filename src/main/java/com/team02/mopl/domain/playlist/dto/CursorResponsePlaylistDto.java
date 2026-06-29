package com.team02.mopl.domain.playlist.dto;

import com.team02.mopl.global.dto.CursorResponse;
import java.util.List;
import java.util.UUID;

public record CursorResponsePlaylistDto(
    List<PlaylistDto> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection) {

  public static CursorResponsePlaylistDto from(CursorResponse<PlaylistDto> response) {
    return new CursorResponsePlaylistDto(
        response.data(),
        response.nextCursor(),
        response.nextIdAfter(),
        response.hasNext(),
        response.totalCount(),
        response.sortBy(),
        response.sortDirection());
  }
}
