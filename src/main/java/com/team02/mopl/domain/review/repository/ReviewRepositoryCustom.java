package com.team02.mopl.domain.review.repository;

import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public interface ReviewRepositoryCustom {

  // 커서 페이지네이션 조회 (정렬키/방향/커서 유무를 동적으로 처리)
  // contentId가 null이면 전체, cursor가 null이면 첫 페이지로 동작한다
  List<Review> findReviewsByCursor(
      UUID contentId,
      ReviewSortBy sortBy,
      SortDirection direction,
      String cursor,
      UUID idAfter,
      int limit);
}
