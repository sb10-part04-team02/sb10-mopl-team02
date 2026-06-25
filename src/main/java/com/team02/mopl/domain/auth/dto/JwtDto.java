package com.team02.mopl.domain.auth.dto;

import jakarta.validation.constraints.NotNull;

public record JwtDto(@NotNull UserDto userDto, @NotNull String accessToken) {}
