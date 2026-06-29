package com.team02.mopl.domain.playlist.dto;

import jakarta.validation.constraints.Size;

public record PlaylistUpdateRequest(
    @Size(max = 100) String title, @Size(max = 255) String description) {}
