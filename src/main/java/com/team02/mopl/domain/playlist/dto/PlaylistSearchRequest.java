package com.team02.mopl.domain.playlist.dto;

import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import java.util.UUID;

public record PlaylistSearchRequest(
    String keyword,
    String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    PlaylistSortBy sortBy)
    implements CursorPageRequest<PlaylistSortBy> {}
