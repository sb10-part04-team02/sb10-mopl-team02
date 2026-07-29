package com.team02.mopl.domain.review.mapper;

import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

  // 이미 조회된 author를 조립 (author 조회·폴백은 서비스 계층에서 처리)
  public ReviewDto toDto(Review review, UserSummary author) {
    return new ReviewDto(
        review.getId(), review.getContentId(), author, review.getText(), review.getRating());
  }

  public UserSummary toUserSummary(User user) {
    return new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl());
  }
}
