package com.team02.mopl.domain.playlist.dto;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistDto(
    UUID id,
    UserSummary owner,
    String title,
    String description,
    Instant updatedAt,
    long subscriberCount,
    boolean subscribedByMe,
    List<ContentSummary> contents) {}
