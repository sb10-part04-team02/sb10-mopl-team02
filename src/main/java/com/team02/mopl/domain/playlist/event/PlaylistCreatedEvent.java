package com.team02.mopl.domain.playlist.event;

import java.util.UUID;

public record PlaylistCreatedEvent(
    UUID ownerId, String ownerName, String playlistTitle, String playlistDescription) {}
