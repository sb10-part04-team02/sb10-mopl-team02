package com.team02.mopl.domain.follow.dto;

import com.team02.mopl.domain.follow.entity.Follow;
import java.time.Instant;
import java.util.UUID;

public record FollowDto(UUID id, Instant createdAt, UUID followerId, UUID followeeId) {
  public static FollowDto from(Follow follow) {
    return new FollowDto(
        follow.getId(),
        follow.getCreatedAt(),
        follow.getFollower().getId(),
        follow.getFollowee().getId());
  }
}
