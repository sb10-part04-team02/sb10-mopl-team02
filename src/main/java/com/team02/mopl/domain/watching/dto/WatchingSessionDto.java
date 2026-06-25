package com.team02.mopl.domain.watching.dto;

import com.team02.mopl.domain.content.dto.ContentSummary;
import java.time.Instant;
import java.util.UUID;


public record WatchingSessionDto(
    UUID id, Instant createdAt, Watcher watcher, ContentSummary content) {}
