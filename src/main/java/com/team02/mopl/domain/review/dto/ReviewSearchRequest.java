package com.team02.mopl.domain.review.dto;

import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import java.util.UUID;

public record ReviewSearchRequest(
    UUID contentId,
    String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    ReviewSortBy sortBy)
    implements CursorPageRequest<ReviewSortBy> {}
