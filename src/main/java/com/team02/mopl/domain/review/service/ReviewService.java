package com.team02.mopl.domain.review.service;

import com.team02.mopl.domain.content.service.ContentRatingService;
import com.team02.mopl.domain.review.dto.ReviewCreateRequest;
import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.dto.ReviewSearchRequest;
import com.team02.mopl.domain.review.dto.ReviewUpdateRequest;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.domain.review.exception.ReviewAlreadyExistsException;
import com.team02.mopl.domain.review.mapper.ReviewMapper;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import com.team02.mopl.domain.review.util.ReviewCursorConverter;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.util.OwnershipValidator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final ReviewMapper reviewMapper;
  private final ContentRatingService contentRatingService;

  // 리뷰 목록 조회 (커서 페이지네이션)
  // contentId로 특정 콘텐츠 리뷰 필터링, createdAt/rating 정렬, 논리 삭제 제외
  // 복합키 (정렬값, id) 비교 (QueryDSL 동적 쿼리)
  public CursorResponse<ReviewDto> getReviews(ReviewSearchRequest request) {
    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction = CursorPageRequest.normalizeSortDirection(request.sortDirection());
    ReviewSortBy sortBy = request.sortBy() != null ? request.sortBy() : ReviewSortBy.CREATED_AT;

    // cursor·idAfter는 항상 함께 와야 한다. 둘 다 없으면 첫 페이지, 하나만 있으면 잘못된 요청
    boolean hasCursor = request.cursor() != null && !request.cursor().isBlank();
    boolean hasIdAfter = request.idAfter() != null;
    if (hasCursor != hasIdAfter) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    Comparable<?> cursor = ReviewCursorConverter.toSortKey(sortBy, request.cursor());

    // hasNext 판정을 위해 limit + 1건을 조회
    List<Review> reviews =
        reviewRepository.findReviewsByCursor(
            request.contentId(), sortBy, direction, cursor, request.idAfter(), limit + 1);

    boolean hasNext = reviews.size() > limit;
    List<Review> page = hasNext ? reviews.subList(0, limit) : reviews;

    List<ReviewDto> data = page.stream().map(reviewMapper::toDto).toList();
    long totalCount = reviewRepository.countActive(request.contentId());

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      Review last = page.get(page.size() - 1);
      nextCursor = ReviewCursorConverter.toCursor(sortBy, last);
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy.name(), direction.name());
  }

  @Transactional
  public ReviewDto createReview(UUID authorId, ReviewCreateRequest request) {
    log.debug("리뷰 생성 시작: authorId={}, contentId={}", authorId, request.contentId());

    if (reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(
        authorId, request.contentId())) {
      throw new ReviewAlreadyExistsException();
    }

    Review review = new Review(authorId, request.contentId(), request.text(), request.rating());

    // 광클이나 동시 요청 대응으로의 2차 체크
    Review savedReview;
    try {
      savedReview = reviewRepository.saveAndFlush(review);
    } catch (DataIntegrityViolationException e) {
      log.warn("리뷰 저장 중 무결성 위반 발생: authorId={}, contentId={}", authorId, request.contentId(), e);
      throw new ReviewAlreadyExistsException();
    }
    contentRatingService.refreshAggregate(request.contentId());
    ReviewDto reviewDto = reviewMapper.toDto(savedReview);

    log.info("리뷰 생성 성공: reviewId={}, authorId={}", reviewDto.id(), authorId);
    return reviewDto;
  }

  @Transactional
  public ReviewDto updateReview(UUID reviewId, UUID requesterId, ReviewUpdateRequest request) {
    log.debug("리뷰 수정 시작: reviewId={}, requesterId={}", reviewId, requesterId);

    Review review =
        reviewRepository
            .findByIdAndDeletedAtIsNull(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

    OwnershipValidator.validateOwner(review.getAuthorId(), requesterId);

    review.update(request.text(), request.rating());
    reviewRepository.flush();
    contentRatingService.refreshAggregate(review.getContentId());
    ReviewDto reviewDto = reviewMapper.toDto(review);

    log.info("리뷰 수정 성공: reviewId={}, requesterId={}", reviewId, requesterId);
    return reviewDto;
  }

  @Transactional
  public void deleteReview(UUID reviewId, UUID requesterId) {
    log.debug("리뷰 삭제 시작: reviewId={}, requesterId={}", reviewId, requesterId);

    Review review =
        reviewRepository
            .findByIdAndDeletedAtIsNull(reviewId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

    OwnershipValidator.validateOwner(review.getAuthorId(), requesterId);

    review.delete();
    reviewRepository.flush();
    contentRatingService.refreshAggregate(review.getContentId());

    log.info("리뷰 삭제 성공: reviewId={}, requesterId={}", reviewId, requesterId);
  }
}
