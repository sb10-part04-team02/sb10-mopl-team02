package com.team02.mopl.domain.content.service;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.review.dto.ReviewAggregate;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRatingService {

  private final ContentRepository contentRepository;
  private final ReviewRepository reviewRepository;

  // 리뷰 변경(생성·수정·삭제) 시 콘텐츠의 평균 평점·리뷰 수를 재집계해 갱신
  // 활성 리뷰를 전부 다시 집계하므로 정합성이 보장됨
  @Transactional
  public void refreshAggregate(UUID contentId) {
    Content content =
        contentRepository
            .findByIdAndDeletedAtIsNull(contentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));

    ReviewAggregate aggregate = reviewRepository.aggregateByContentId(contentId);
    int reviewCount = (int) aggregate.reviewCount();
    double averageRating = aggregate.averageRating() != null ? aggregate.averageRating() : 0.0;

    content.applyRatingAggregate(averageRating, reviewCount);
    log.debug(
        "content.rating_refreshed contentId={} averageRating={} reviewCount={}",
        contentId,
        averageRating,
        reviewCount);
  }
}
