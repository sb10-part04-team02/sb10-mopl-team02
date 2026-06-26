package com.team02.mopl.domain.review.service;

import com.team02.mopl.domain.review.dto.ReviewCreateRequest;
import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.dto.ReviewSearchRequest;
import com.team02.mopl.domain.review.dto.ReviewUpdateRequest;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.domain.review.exception.ReviewAlreadyExistsException;
import com.team02.mopl.domain.review.mapper.ReviewMapper;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.util.OwnershipValidator;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final ReviewMapper reviewMapper;

  // 리뷰 목록 조회 (커서 페이지네이션)
  // contentId로 특정 콘텐츠 리뷰 필터링, createdAt/rating 정렬, 논리 삭제 제외
  // 복합키 (정렬값, id) 비교로 안정적인 페이징을 보장한다
  public CursorResponse<ReviewDto> getReviews(ReviewSearchRequest request) {
    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction = CursorPageRequest.normalizeSortDirection(request.sortDirection());
    ReviewSortBy sortBy = request.sortBy() != null ? request.sortBy() : ReviewSortBy.CREATED_AT;
    boolean ascending = direction == SortDirection.ASCENDING;

    // hasNext 판정을 위해 limit + 1건을 조회한다
    Pageable pageable = PageRequest.of(0, limit + 1);
    boolean firstPage = request.cursor() == null || request.idAfter() == null;

    List<Review> reviews =
        sortBy == ReviewSortBy.RATING
            ? fetchByRating(request, ascending, firstPage, pageable)
            : fetchByCreatedAt(request, ascending, firstPage, pageable);

    boolean hasNext = reviews.size() > limit;
    List<Review> page = hasNext ? reviews.subList(0, limit) : reviews;

    List<ReviewDto> data = page.stream().map(reviewMapper::toDto).toList();
    long totalCount = reviewRepository.countActive(request.contentId());

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      Review last = page.get(page.size() - 1);
      nextCursor = encodeCursor(sortBy, last);
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy.name(), direction.name());
  }

  private List<Review> fetchByCreatedAt(
      ReviewSearchRequest request, boolean ascending, boolean firstPage, Pageable pageable) {
    if (firstPage) {
      return ascending
          ? reviewRepository.findFirstByCreatedAtAsc(request.contentId(), pageable)
          : reviewRepository.findFirstByCreatedAtDesc(request.contentId(), pageable);
    }
    Instant cursor = parseInstantCursor(request.cursor());
    return ascending
        ? reviewRepository.findNextByCreatedAtAsc(
            request.contentId(), cursor, request.idAfter(), pageable)
        : reviewRepository.findNextByCreatedAtDesc(
            request.contentId(), cursor, request.idAfter(), pageable);
  }

  private List<Review> fetchByRating(
      ReviewSearchRequest request, boolean ascending, boolean firstPage, Pageable pageable) {
    if (firstPage) {
      return ascending
          ? reviewRepository.findFirstByRatingAsc(request.contentId(), pageable)
          : reviewRepository.findFirstByRatingDesc(request.contentId(), pageable);
    }
    double cursor = parseDoubleCursor(request.cursor());
    return ascending
        ? reviewRepository.findNextByRatingAsc(
            request.contentId(), cursor, request.idAfter(), pageable)
        : reviewRepository.findNextByRatingDesc(
            request.contentId(), cursor, request.idAfter(), pageable);
  }

  // 정렬값을 원문 문자열로 인코딩 (createdAt: ISO-8601, rating: 숫자)
  private String encodeCursor(ReviewSortBy sortBy, Review review) {
    return sortBy == ReviewSortBy.RATING
        ? Double.toString(review.getRating())
        : review.getCreatedAt().toString();
  }

  private Instant parseInstantCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
  }

  private double parseDoubleCursor(String cursor) {
    try {
      return Double.parseDouble(cursor);
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
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

    log.info("리뷰 삭제 성공: reviewId={}, requesterId={}", reviewId, requesterId);
  }
}
