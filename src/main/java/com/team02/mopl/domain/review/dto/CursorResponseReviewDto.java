package com.team02.mopl.domain.review.dto;

import com.team02.mopl.global.dto.CursorResponse;
import java.util.List;
import java.util.UUID;

public record CursorResponseReviewDto(
    List<ReviewDto> data,
    String nextCursor,
    UUID nextIdAfter,
    boolean hasNext,
    long totalCount,
    String sortBy,
    String sortDirection) {

  public static CursorResponseReviewDto from(CursorResponse<ReviewDto> response) {
    return new CursorResponseReviewDto(
        response.data(),
        response.nextCursor(),
        response.nextIdAfter(),
        response.hasNext(),
        response.totalCount(),
        response.sortBy(),
        response.sortDirection());
  }
}
