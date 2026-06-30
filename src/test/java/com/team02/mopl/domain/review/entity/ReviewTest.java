package com.team02.mopl.domain.review.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewTest {

  private final UUID authorId = UUID.randomUUID();
  private final UUID contentId = UUID.randomUUID();

  @Nested
  class Create {

    @Test
    @DisplayName("유효한 값이면 필드가 그대로 채워진다")
    void success_whenValid() {
      // when
      Review review = new Review(authorId, contentId, "재밌어요", 4.5);

      // then
      assertThat(review.getAuthorId()).isEqualTo(authorId);
      assertThat(review.getContentId()).isEqualTo(contentId);
      assertThat(review.getText()).isEqualTo("재밌어요");
      assertThat(review.getRating()).isEqualTo(4.5);
    }

    @ParameterizedTest
    @DisplayName("rating 경계값(0.0, 5.0)이면 생성에 성공한다")
    @ValueSource(doubles = {0.0, 5.0})
    void success_whenRatingOnBoundary(double rating) {
      // when & then
      assertThatNoException().isThrownBy(() -> new Review(authorId, contentId, "내용", rating));
    }

    @Test
    @DisplayName("authorId가 null이면 NullPointerException을 던진다")
    void fail_whenAuthorIdNull() {
      // when & then
      assertThatThrownBy(() -> new Review(null, contentId, "내용", 4.0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("authorId");
    }

    @Test
    @DisplayName("contentId가 null이면 NullPointerException을 던진다")
    void fail_whenContentIdNull() {
      // when & then
      assertThatThrownBy(() -> new Review(authorId, null, "내용", 4.0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("contentId");
    }

    @Test
    @DisplayName("text가 null이면 NullPointerException을 던진다")
    void fail_whenTextNull() {
      // when & then
      assertThatThrownBy(() -> new Review(authorId, contentId, null, 4.0))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("text");
    }

    @ParameterizedTest
    @DisplayName("rating이 0.0~5.0 범위를 벗어나면 IllegalArgumentException을 던진다")
    @ValueSource(doubles = {-0.1, 5.1})
    void fail_whenRatingOutOfRange(double rating) {
      // when & then
      assertThatThrownBy(() -> new Review(authorId, contentId, "내용", rating))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  class Update {

    @Test
    @DisplayName("text와 rating이 모두 주어지면 둘 다 변경된다")
    void update_bothFields() {
      // given
      Review review = new Review(authorId, contentId, "원본", 4.5);

      // when
      review.update("수정된 내용", 2.0);

      // then
      assertThat(review.getText()).isEqualTo("수정된 내용");
      assertThat(review.getRating()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("text가 null이면 기존 내용을 유지하고 rating만 변경한다")
    void update_keepsTextWhenNull() {
      // given
      Review review = new Review(authorId, contentId, "원본", 4.5);

      // when
      review.update(null, 1.0);

      // then
      assertThat(review.getText()).isEqualTo("원본");
      assertThat(review.getRating()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("rating이 null이면 기존 평점을 유지하고 text만 변경한다")
    void update_keepsRatingWhenNull() {
      // given
      Review review = new Review(authorId, contentId, "원본", 4.5);

      // when
      review.update("수정된 내용", null);

      // then
      assertThat(review.getText()).isEqualTo("수정된 내용");
      assertThat(review.getRating()).isEqualTo(4.5);
    }

    @ParameterizedTest
    @DisplayName("수정 시 rating이 0.0~5.0 범위를 벗어나면 IllegalArgumentException을 던진다")
    @ValueSource(doubles = {-0.1, 5.1})
    void update_fail_whenRatingOutOfRange(double rating) {
      // given
      Review review = new Review(authorId, contentId, "원본", 4.5);

      // when & then
      assertThatThrownBy(() -> review.update("내용", rating))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
