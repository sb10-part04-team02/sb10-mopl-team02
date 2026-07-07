package com.team02.mopl.domain.playlist.event;

import java.util.UUID;

public record PlaylistContentAddedEvent(
    UUID playlistId, String playlistTitle, UUID contentId, String contentTitle) {}
