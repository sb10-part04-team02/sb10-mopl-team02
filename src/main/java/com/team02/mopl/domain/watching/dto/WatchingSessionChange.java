package com.team02.mopl.domain.watching.dto;

import com.team02.mopl.domain.watching.enums.ChangeType;

public record WatchingSessionChange(
    ChangeType type, WatchingSessionDto watchingSession, long watcherCount) {}
