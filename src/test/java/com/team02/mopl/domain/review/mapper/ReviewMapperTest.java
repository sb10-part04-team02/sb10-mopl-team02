package com.team02.mopl.domain.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewMapperTest {

  private final ReviewMapper reviewMapper = new ReviewMapper();

  @Test
  @DisplayName("toDto는 기본 필드를 매핑하고 전달받은 author를 그대로 조립한다")
  void toDto_mapsFieldsAndAssemblesAuthor() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    Review review = new Review(authorId, contentId, "재밌어요", 4.5);
    UserSummary author = new UserSummary(authorId, "홍길동", "http://img/author");

    ReviewDto dto = reviewMapper.toDto(review, author);

    assertThat(dto.contentId()).isEqualTo(contentId);
    assertThat(dto.text()).isEqualTo("재밌어요");
    assertThat(dto.rating()).isEqualTo(4.5);
    assertThat(dto.author()).isEqualTo(author);
  }

  @Test
  @DisplayName("toUserSummary는 User의 id·이름·프로필 이미지를 매핑한다")
  void toUserSummary_mapsUserFields() {
    User user = new User("홍길동", "hong@test.com", "pw", "http://img/author", Role.USER, false);
    UUID userId = UUID.randomUUID();
    ReflectionTestUtils.setField(user, "id", userId);

    UserSummary summary = reviewMapper.toUserSummary(user);

    assertThat(summary.userId()).isEqualTo(userId);
    assertThat(summary.name()).isEqualTo("홍길동");
    assertThat(summary.profileImageUrl()).isEqualTo("http://img/author");
  }
}
