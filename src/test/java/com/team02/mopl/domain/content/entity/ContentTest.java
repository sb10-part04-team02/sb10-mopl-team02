package com.team02.mopl.domain.content.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.content.enums.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ContentTest {

  private Content newContent() {
    return new Content(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", "thumb-url");
  }

  @Nested
  class Create {

    @Test
    @DisplayName("유효한 값이면 필드가 그대로 채워지고 평점 집계는 0으로 초기화된다")
    void success_whenValid() {
      // when
      Content content = new Content(ContentType.MOVIE, "인셉션", "꿈 속의 꿈", "thumb-url");

      // then
      assertThat(content.getContentType()).isEqualTo(ContentType.MOVIE);
      assertThat(content.getTitle()).isEqualTo("인셉션");
      assertThat(content.getDescription()).isEqualTo("꿈 속의 꿈");
      assertThat(content.getThumbnailUrl()).isEqualTo("thumb-url");
      assertThat(content.getAverageRating()).isEqualTo(0.0);
      assertThat(content.getReviewCount()).isZero();
    }

    @Test
    @DisplayName("contentType이 null이면 NullPointerException을 던진다")
    void fail_whenContentTypeNull() {
      // when & then
      assertThatThrownBy(() -> new Content(null, "인셉션", "설명", "thumb-url"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("contentType");
    }

    @Test
    @DisplayName("title이 null이면 NullPointerException을 던진다")
    void fail_whenTitleNull() {
      // when & then
      assertThatThrownBy(() -> new Content(ContentType.MOVIE, null, "설명", "thumb-url"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("title");
    }

    @Test
    @DisplayName("description이 null이면 NullPointerException을 던진다")
    void fail_whenDescriptionNull() {
      // when & then
      assertThatThrownBy(() -> new Content(ContentType.MOVIE, "인셉션", null, "thumb-url"))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("description");
    }

    @Test
    @DisplayName("thumbnailUrl이 null이면 NullPointerException을 던진다")
    void fail_whenThumbnailUrlNull() {
      // when & then
      assertThatThrownBy(() -> new Content(ContentType.MOVIE, "인셉션", "설명", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("thumbnailUrl");
    }

    @ParameterizedTest
    @DisplayName("title이 공백이면 IllegalArgumentException을 던진다")
    @ValueSource(strings = {"", "  "})
    void fail_whenTitleBlank(String title) {
      // when & then
      assertThatThrownBy(() -> new Content(ContentType.MOVIE, title, "설명", "thumb-url"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("title");
    }

    @ParameterizedTest
    @DisplayName("description이 공백이면 IllegalArgumentException을 던진다")
    @ValueSource(strings = {"", "  "})
    void fail_whenDescriptionBlank(String description) {
      // when & then
      assertThatThrownBy(() -> new Content(ContentType.MOVIE, "인셉션", description, "thumb-url"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("description");
    }

    @ParameterizedTest
    @DisplayName("thumbnailUrl이 공백이면 IllegalArgumentException을 던진다")
    @ValueSource(strings = {"", "  "})
    void fail_whenThumbnailUrlBlank(String thumbnailUrl) {
      // when & then
      assertThatThrownBy(() -> new Content(ContentType.MOVIE, "인셉션", "설명", thumbnailUrl))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("thumbnailUrl");
    }
  }

  @Nested
  class Update {

    @Test
    @DisplayName("title과 description이 모두 주어지면 둘 다 변경된다")
    void success_updatesTitleAndDescription_whenTextGiven() {
      // given
      Content content = newContent();

      // when
      content.update("새 제목", "새 설명");

      // then
      assertThat(content.getTitle()).isEqualTo("새 제목");
      assertThat(content.getDescription()).isEqualTo("새 설명");
    }

    @ParameterizedTest
    @DisplayName("title이 null이나 공백이면 기존 제목을 유지하고 description만 변경한다")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void keepsExistingTitle_whenTitleNullOrBlank(String title) {
      // given
      Content content = newContent();

      // when
      content.update(title, "새 설명");

      // then
      assertThat(content.getTitle()).isEqualTo("인셉션");
      assertThat(content.getDescription()).isEqualTo("새 설명");
    }

    @ParameterizedTest
    @DisplayName("description이 null이나 공백이면 기존 설명을 유지하고 title만 변경한다")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void keepsExistingDescription_whenDescriptionNullOrBlank(String description) {
      // given
      Content content = newContent();

      // when
      content.update("새 제목", description);

      // then
      assertThat(content.getTitle()).isEqualTo("새 제목");
      assertThat(content.getDescription()).isEqualTo("꿈 속의 꿈");
    }
  }

  @Nested
  class ChangeThumbnailUrl {

    @Test
    @DisplayName("텍스트가 주어지면 썸네일 URL이 변경된다")
    void success_changesUrl_whenTextGiven() {
      // given
      Content content = newContent();

      // when
      content.changeThumbnailUrl("new-thumb-url");

      // then
      assertThat(content.getThumbnailUrl()).isEqualTo("new-thumb-url");
    }

    @ParameterizedTest
    @DisplayName("null이나 공백이면 기존 썸네일 URL을 유지한다")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void keepsExistingUrl_whenNullOrBlank(String thumbnailUrl) {
      // given
      Content content = newContent();

      // when
      content.changeThumbnailUrl(thumbnailUrl);

      // then
      assertThat(content.getThumbnailUrl()).isEqualTo("thumb-url");
    }
  }
}
