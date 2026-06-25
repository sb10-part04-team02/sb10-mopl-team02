package com.team02.mopl.domain.review.mapper;

import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.user.dto.UserSummary;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

  // TODO: 사용자 조회 연동 시 UserService를 주입하여 author의 name/profileImageUrl을 채운다.
  //             현재는 authorId(userId)만 채운 스텁으로 생성한다.
  public ReviewDto toDto(Review review) {
    return new ReviewDto(
        review.getId(),
        review.getContentId(),
        new UserSummary(review.getAuthorId(), null, null),
        review.getText(),
        review.getRating());
  }
}
