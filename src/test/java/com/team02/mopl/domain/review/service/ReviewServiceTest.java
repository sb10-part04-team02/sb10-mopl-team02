package com.team02.mopl.domain.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.review.dto.ReviewCreateRequest;
import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.exception.ReviewAlreadyExistsException;
import com.team02.mopl.domain.review.mapper.ReviewMapper;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @Mock ReviewRepository reviewRepository;

  @Mock ReviewMapper reviewMapper;

  @InjectMocks ReviewService reviewService;

  @Captor ArgumentCaptor<Review> reviewCaptor;

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
      then(reviewRepository).should().saveAndFlush(reviewCaptor.capture());
      then(reviewMapper).should().toDto(any(Review.class));

      Review captured = reviewCaptor.getValue();
      assertThat(captured.getAuthorId()).isEqualTo(authorId);
      assertThat(captured.getContentId()).isEqualTo(contentId);
      assertThat(captured.getText()).isEqualTo(text);
      assertThat(captured.getRating()).isEqualTo(rating);
    }
  }
}
