package com.team02.mopl.domain.dm.dto;

import jakarta.validation.constraints.NotBlank;

public record DirectMessageSendRequest(@NotBlank String content) {}
