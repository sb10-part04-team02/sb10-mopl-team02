package com.team02.mopl.domain.follow.event;

import java.util.UUID;

public record FollowCreatedEvent(UUID followerId, String followerName, UUID followeeId) {}
