package com.team02.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.review.dto.ReviewAggregate;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentRatingService 단위 테스트")
class ContentRatingServiceTest {

  @Mock ContentRepository contentRepository;

  @Mock ReviewRepository reviewRepository;

  @InjectMocks ContentRatingService contentRatingService;

  private final UUID contentId = UUID.randomUUID();

  private Content content() {
    return new Content(ContentType.MOVIE, "제목", "설명", "thumb");
  }

  @Test
  @DisplayName("활성 리뷰가 존재하면 평균 평점·리뷰 수를 갱신한다")
  void success_whenReviewsExist() {
    // given
    Content content = content();
    given(contentRepository.findByIdAndDeletedAtIsNull(eq(contentId)))
        .willReturn(Optional.of(content));
    given(reviewRepository.aggregateByContentId(eq(contentId)))
        .willReturn(new ReviewAggregate(3L, 4.0));

    // when
    contentRatingService.refreshAggregate(contentId);

    // then
    assertThat(content.getReviewCount()).isEqualTo(3);
    assertThat(content.getAverageRating()).isEqualTo(4.0);
  }

  @Test
  @DisplayName("활성 리뷰가 없으면 평균 평점은 0.0, 리뷰 수는 0으로 갱신한다")
  void success_whenNoReviews() {
    // given
    Content content = content();
    given(contentRepository.findByIdAndDeletedAtIsNull(eq(contentId)))
        .willReturn(Optional.of(content));
    given(reviewRepository.aggregateByContentId(eq(contentId)))
        .willReturn(new ReviewAggregate(0L, null));

    // when
    contentRatingService.refreshAggregate(contentId);

    // then
    assertThat(content.getReviewCount()).isEqualTo(0);
    assertThat(content.getAverageRating()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("콘텐츠가 존재하지 않으면 CONTENT_NOT_FOUND BusinessException을 던진다")
  void fail_whenContentNotFound() {
    // given
    given(contentRepository.findByIdAndDeletedAtIsNull(eq(contentId))).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> contentRatingService.refreshAggregate(contentId))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.CONTENT_NOT_FOUND);
  }
}
