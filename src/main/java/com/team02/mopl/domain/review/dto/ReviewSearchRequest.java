package com.team02.mopl.domain.review.dto;

import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.domain.review.enums.SortDirection;
import java.util.UUID;

public record ReviewSearchRequest(
    UUID contentId,
    String cursor,
    UUID idAfter,
    int limit,
    SortDirection sortDirection,
    ReviewSortBy sortBy) {}
