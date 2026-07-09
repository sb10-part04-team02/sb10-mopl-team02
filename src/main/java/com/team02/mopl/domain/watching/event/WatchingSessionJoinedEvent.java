package com.team02.mopl.domain.watching.event;

import java.util.UUID;

public record WatchingSessionJoinedEvent(
    UUID watcherId, String watcherName, UUID contentId, String contentTitle) {}
