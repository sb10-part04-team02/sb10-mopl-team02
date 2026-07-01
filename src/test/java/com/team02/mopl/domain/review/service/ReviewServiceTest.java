package com.team02.mopl.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

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
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @Mock ReviewRepository reviewRepository;

  @Mock ReviewMapper reviewMapper;

  @Mock ContentRatingService contentRatingService;

  @InjectMocks ReviewService reviewService;

  @Captor ArgumentCaptor<Review> reviewCaptor;

  @Nested
  class GetReviews {
    private final UUID contentId = UUID.randomUUID();

    private Review review(double rating) {
      Review review = new Review(UUID.randomUUID(), contentId, "리뷰", rating);
      ReflectionTestUtils.setField(review, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(review, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
      return review;
    }

    @Test
    @DisplayName("커서가 없으면 첫 페이지를 createdAt 내림차순으로 조회한다")
    void firstPage_byCreatedAtDesc() {
      // given
      ReviewSearchRequest request =
          new ReviewSearchRequest(
              contentId, null, null, 10, SortDirection.DESCENDING, ReviewSortBy.CREATED_AT);
      given(
              reviewRepository.findReviewsByCursor(
                  eq(contentId),
                  eq(ReviewSortBy.CREATED_AT),
                  eq(SortDirection.DESCENDING),
                  eq(null),
                  eq(null),
                  eq(11)))
          .willReturn(List.of(review(4.0), review(3.0)));
      given(reviewRepository.countActive(eq(contentId))).willReturn(2L);
      given(reviewMapper.toDto(any(Review.class)))
          .willReturn(
              new ReviewDto(
                  UUID.randomUUID(),
                  contentId,
                  new UserSummary(UUID.randomUUID(), null, null),
                  "리뷰",
                  4.0));

      // when
      CursorResponse<ReviewDto> response = reviewService.getReviews(request);

      // then
      assertThat(response.data()).hasSize(2);
      assertThat(response.hasNext()).isFalse();
      assertThat(response.nextCursor()).isNull();
      assertThat(response.nextIdAfter()).isNull();
      assertThat(response.totalCount()).isEqualTo(2L);
      assertThat(response.sortBy()).isEqualTo("CREATED_AT");
      assertThat(response.sortDirection()).isEqualTo("DESCENDING");
    }

    @Test
    @DisplayName("limit + 1건이 조회되면 hasNext=true이고 마지막 항목으로 nextCursor를 채운다")
    void hasNext_whenMoreThanLimit() {
      // given
      ReviewSearchRequest request =
          new ReviewSearchRequest(
              contentId, null, null, 1, SortDirection.DESCENDING, ReviewSortBy.CREATED_AT);
      Review first = review(4.0);
      given(
              reviewRepository.findReviewsByCursor(
                  eq(contentId),
                  eq(ReviewSortBy.CREATED_AT),
                  eq(SortDirection.DESCENDING),
                  eq(null),
                  eq(null),
                  eq(2)))
          .willReturn(List.of(first, review(3.0)));
      given(reviewRepository.countActive(eq(contentId))).willReturn(2L);
      given(reviewMapper.toDto(any(Review.class)))
          .willReturn(
              new ReviewDto(
                  first.getId(),
                  contentId,
                  new UserSummary(UUID.randomUUID(), null, null),
                  "리뷰",
                  4.0));

      // when
      CursorResponse<ReviewDto> response = reviewService.getReviews(request);

      // then
      assertThat(response.data()).hasSize(1);
      assertThat(response.hasNext()).isTrue();
      assertThat(response.nextCursor()).isEqualTo(first.getCreatedAt().toString());
      assertThat(response.nextIdAfter()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("rating 정렬이고 다음 페이지가 있으면 nextCursor를 마지막 항목의 평점으로 인코딩한다")
    void nextCursor_encodedAsRating_whenSortByRating() {
      // given
      ReviewSearchRequest request =
          new ReviewSearchRequest(
              contentId, null, null, 1, SortDirection.DESCENDING, ReviewSortBy.RATING);
      Review first = review(4.0);
      given(
              reviewRepository.findReviewsByCursor(
                  eq(contentId),
                  eq(ReviewSortBy.RATING),
                  eq(SortDirection.DESCENDING),
                  eq(null),
                  eq(null),
                  eq(2)))
          .willReturn(List.of(first, review(3.0)));
      given(reviewRepository.countActive(eq(contentId))).willReturn(2L);
      given(reviewMapper.toDto(any(Review.class)))
          .willReturn(
              new ReviewDto(
                  first.getId(),
                  contentId,
                  new UserSummary(UUID.randomUUID(), null, null),
                  "리뷰",
                  4.0));

      // when
      CursorResponse<ReviewDto> response = reviewService.getReviews(request);

      // then
      assertThat(response.hasNext()).isTrue();
      assertThat(response.nextCursor()).isEqualTo(Double.toString(first.getRating()));
      assertThat(response.nextIdAfter()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("rating 정렬에서 커서가 있으면 다음 페이지를 평점 기준으로 조회한다")
    void nextPage_byRating() {
      // given
      UUID idAfter = UUID.randomUUID();
      ReviewSearchRequest request =
          new ReviewSearchRequest(
              contentId, "4.0", idAfter, 10, SortDirection.DESCENDING, ReviewSortBy.RATING);
      given(
              reviewRepository.findReviewsByCursor(
                  eq(contentId),
                  eq(ReviewSortBy.RATING),
                  eq(SortDirection.DESCENDING),
                  eq("4.0"),
                  eq(idAfter),
                  eq(11)))
          .willReturn(List.of(review(3.0)));
      given(reviewRepository.countActive(eq(contentId))).willReturn(5L);
      given(reviewMapper.toDto(any(Review.class)))
          .willReturn(
              new ReviewDto(
                  UUID.randomUUID(),
                  contentId,
                  new UserSummary(UUID.randomUUID(), null, null),
                  "리뷰",
                  3.0));

      // when
      CursorResponse<ReviewDto> response = reviewService.getReviews(request);

      // then
      assertThat(response.data()).hasSize(1);
      assertThat(response.sortBy()).isEqualTo("RATING");
      then(reviewRepository)
          .should()
          .findReviewsByCursor(
              eq(contentId),
              eq(ReviewSortBy.RATING),
              eq(SortDirection.DESCENDING),
              eq("4.0"),
              eq(idAfter),
              eq(11));
    }
  }

  @Nested
  class CreateReview {
    private final UUID authorId = UUID.randomUUID();
    private final UUID contentId = UUID.randomUUID();
    private final String text = "재밌어요";
    private final double rating = 4.5;

    @Test
    @DisplayName("이미 작성한 리뷰가 존재하면 ReviewAlreadyExistsException을 던진다")
    void fail_whenReviewAlreadyExists() {
      // given
      ReviewCreateRequest request = new ReviewCreateRequest(contentId, text, rating);
      given(
              reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(
                  eq(authorId), eq(contentId)))
          .willReturn(true);

      // when & then
      assertThrows(
          ReviewAlreadyExistsException.class, () -> reviewService.createReview(authorId, request));
    }

    @Test
    @DisplayName("동시 요청으로 DB 저장 시 Unique 제약조건이 위반되면 ReviewAlreadyExistsException을 던진다")
    void fail_whenDbUniqueConstraintViolated() {
      // given
      ReviewCreateRequest request = new ReviewCreateRequest(contentId, text, rating);
      given(
              reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(
                  eq(authorId), eq(contentId)))
          .willReturn(false);
      given(reviewRepository.saveAndFlush(any(Review.class)))
          .willThrow(new DataIntegrityViolationException("Duplicate Review"));

      // when & then
      assertThrows(
          ReviewAlreadyExistsException.class, () -> reviewService.createReview(authorId, request));
    }

    @Test
    @DisplayName("정상 요청이면 작성자를 요청자로 지정해 저장하고 ReviewDto를 반환한다")
    void success_whenRequestIsValid() {
      // given
      ReviewCreateRequest request = new ReviewCreateRequest(contentId, text, rating);
      Review savedReview = new Review(authorId, contentId, text, rating);
      ReviewDto expect =
          new ReviewDto(
              UUID.randomUUID(), contentId, new UserSummary(authorId, null, null), text, rating);
      given(
              reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(
                  eq(authorId), eq(contentId)))
          .willReturn(false);
      given(reviewRepository.saveAndFlush(any(Review.class))).willReturn(savedReview);
      given(reviewMapper.toDto(any(Review.class))).willReturn(expect);

      // when
      ReviewDto actual = reviewService.createReview(authorId, request);

      // then
      assertThat(actual).isEqualTo(expect);
      then(reviewRepository)
          .should()
          .existsByAuthorIdAndContentIdAndDeletedAtIsNull(eq(authorId), eq(contentId));
      // 재집계가 저장 이후 최신 값을 보도록 saveAndFlush()가 refreshAggregate()보다 먼저 호출돼야 한다
      InOrder inOrder = inOrder(reviewRepository, contentRatingService);
      inOrder.verify(reviewRepository).saveAndFlush(reviewCaptor.capture());
      inOrder.verify(contentRatingService).refreshAggregate(eq(contentId));
      then(reviewMapper).should().toDto(any(Review.class));

      Review captured = reviewCaptor.getValue();
      assertThat(captured.getAuthorId()).isEqualTo(authorId);
      assertThat(captured.getContentId()).isEqualTo(contentId);
      assertThat(captured.getText()).isEqualTo(text);
      assertThat(captured.getRating()).isEqualTo(rating);
    }
  }

  @Nested
  class UpdateReview {
    private final UUID reviewId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID contentId = UUID.randomUUID();

    @Test
    @DisplayName("리뷰가 존재하지 않으면 REVIEW_NOT_FOUND BusinessException을 던진다")
    void fail_whenReviewNotFound() {
      // given
      ReviewUpdateRequest request = new ReviewUpdateRequest("수정", 3.0);
      given(reviewRepository.findByIdAndDeletedAtIsNull(eq(reviewId))).willReturn(Optional.empty());

      // when & then
      BusinessException exception =
          assertThrows(
              BusinessException.class,
              () -> reviewService.updateReview(reviewId, authorId, request));
      assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("작성자가 아니면 FORBIDDEN BusinessException을 던진다")
    void fail_whenRequesterIsNotAuthor() {
      // given
      UUID otherUserId = UUID.randomUUID();
      ReviewUpdateRequest request = new ReviewUpdateRequest("수정", 3.0);
      Review review = new Review(authorId, contentId, "원본", 4.5);
      given(reviewRepository.findByIdAndDeletedAtIsNull(eq(reviewId)))
          .willReturn(Optional.of(review));

      // when & then
      BusinessException exception =
          assertThrows(
              BusinessException.class,
              () -> reviewService.updateReview(reviewId, otherUserId, request));
      assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("작성자 본인이 평점·내용을 수정하면 변경된 ReviewDto를 반환한다")
    void success_whenRequesterIsAuthor() {
      // given
      ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 내용", 2.0);
      Review review = new Review(authorId, contentId, "원본", 4.5);
      ReviewDto expect =
          new ReviewDto(reviewId, contentId, new UserSummary(authorId, null, null), "수정된 내용", 2.0);
      given(reviewRepository.findByIdAndDeletedAtIsNull(eq(reviewId)))
          .willReturn(Optional.of(review));
      given(reviewMapper.toDto(any(Review.class))).willReturn(expect);

      // when
      ReviewDto actual = reviewService.updateReview(reviewId, authorId, request);

      // then
      assertThat(actual).isEqualTo(expect);
      assertThat(review.getText()).isEqualTo("수정된 내용");
      assertThat(review.getRating()).isEqualTo(2.0);
      // 재집계가 DB 반영 이후 최신 값을 보도록 flush()가 refreshAggregate()보다 먼저 호출돼야 한다
      InOrder inOrder = inOrder(reviewRepository, contentRatingService);
      inOrder.verify(reviewRepository).flush();
      inOrder.verify(contentRatingService).refreshAggregate(eq(contentId));
      then(reviewMapper).should().toDto(any(Review.class));
    }

    @Test
    @DisplayName("null 필드는 기존 값을 유지한다")
    void success_whenPartialUpdate() {
      // given
      ReviewUpdateRequest request = new ReviewUpdateRequest(null, 1.0);
      Review review = new Review(authorId, contentId, "원본", 4.5);
      given(reviewRepository.findByIdAndDeletedAtIsNull(eq(reviewId)))
          .willReturn(Optional.of(review));
      given(reviewMapper.toDto(any(Review.class))).willReturn(null);

      // when
      reviewService.updateReview(reviewId, authorId, request);

      // then
      assertThat(review.getText()).isEqualTo("원본");
      assertThat(review.getRating()).isEqualTo(1.0);
    }
  }

  @Nested
  class DeleteReview {
    private final UUID reviewId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final UUID contentId = UUID.randomUUID();

    @Test
    @DisplayName("리뷰가 존재하지 않으면 REVIEW_NOT_FOUND BusinessException을 던진다")
    void fail_whenReviewNotFound() {
      // given
      given(reviewRepository.findByIdAndDeletedAtIsNull(eq(reviewId))).willReturn(Optional.empty());

      // when & then
      BusinessException exception =
          assertThrows(
              BusinessException.class, () -> reviewService.deleteReview(reviewId, authorId));
      assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    @Test
    @DisplayName("작성자가 아니면 FORBIDDEN BusinessException을 던진다")
    void fail_whenRequesterIsNotAuthor() {
      // given
      UUID otherUserId = UUID.randomUUID();
      Review review = new Review(authorId, contentId, "원본", 4.5);
      given(reviewRepository.findByIdAndDeletedAtIsNull(eq(reviewId)))
          .willReturn(Optional.of(review));

      // when & then
      BusinessException exception =
          assertThrows(
              BusinessException.class, () -> reviewService.deleteReview(reviewId, otherUserId));
      assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
      assertThat(review.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("작성자 본인이 삭제하면 리뷰가 논리 삭제된다")
    void success_whenRequesterIsAuthor() {
      // given
      Review review = new Review(authorId, contentId, "원본", 4.5);
      given(reviewRepository.findByIdAndDeletedAtIsNull(eq(reviewId)))
          .willReturn(Optional.of(review));

      // when
      reviewService.deleteReview(reviewId, authorId);

      // then
      assertThat(review.isDeleted()).isTrue();
      // 재집계가 DB 반영 이후 최신 값을 보도록 flush()가 refreshAggregate()보다 먼저 호출돼야 한다
      InOrder inOrder = inOrder(reviewRepository, contentRatingService);
      inOrder.verify(reviewRepository).flush();
      inOrder.verify(contentRatingService).refreshAggregate(eq(contentId));
    }
  }
}
