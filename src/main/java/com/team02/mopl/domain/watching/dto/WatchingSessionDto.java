package com.team02.mopl.domain.watching.dto;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record WatchingSessionDto(
    UUID id, Instant createdAt, UserSummary watcher, ContentSummary content) {}
