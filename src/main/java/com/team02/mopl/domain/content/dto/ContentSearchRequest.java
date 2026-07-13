package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public record ContentSearchRequest(
    ContentType typeEqual,
    String keywordLike,
    List<String> tagsIn,
    String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    SortBy sortBy)
    implements CursorPageRequest<SortBy> {}
