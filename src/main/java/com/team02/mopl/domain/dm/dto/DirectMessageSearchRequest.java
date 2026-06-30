package com.team02.mopl.domain.dm.dto;

import com.team02.mopl.domain.dm.enums.DirectMessageSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import java.util.UUID;

public record DirectMessageSearchRequest(
    String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    DirectMessageSortBy sortBy)
    implements CursorPageRequest<DirectMessageSortBy> {}
