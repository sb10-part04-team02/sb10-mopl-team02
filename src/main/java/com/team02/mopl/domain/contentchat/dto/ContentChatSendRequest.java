package com.team02.mopl.domain.contentchat.dto;

import jakarta.validation.constraints.NotBlank;

public record ContentChatSendRequest(@NotBlank String content) {}
