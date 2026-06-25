package com.team02.mopl.domain.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.entity.Review;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewMapperTest {

  private final ReviewMapper reviewMapper = new ReviewMapper();

  @Test
  @DisplayName("Review 엔티티를 ReviewDto로 변환하면 기본 필드가 매핑되고 author는 authorId 스텁이 된다")
  void toDto_mapsFieldsAndStubsAuthor() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Review review = new Review(authorId, contentId, "재밌어요", 4.5);

    ReviewDto dto = reviewMapper.toDto(review);

    assertThat(dto.contentId()).isEqualTo(contentId);
    assertThat(dto.text()).isEqualTo("재밌어요");
    assertThat(dto.rating()).isEqualTo(4.5);
    assertThat(dto.author().userId()).isEqualTo(authorId);
    assertThat(dto.author().name()).isNull();
    assertThat(dto.author().profileImageUrl()).isNull();
  }
}
