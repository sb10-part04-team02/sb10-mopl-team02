package com.team02.mopl.domain.follow.dto;

import com.team02.mopl.domain.follow.entity.Follow;
import java.util.UUID;

public record FollowDto(UUID id, UUID followeeId, UUID followerId) {

  public static FollowDto from(Follow follow) {
    return new FollowDto(
        follow.getId(), follow.getFollowee().getId(), follow.getFollower().getId());
  }
}
