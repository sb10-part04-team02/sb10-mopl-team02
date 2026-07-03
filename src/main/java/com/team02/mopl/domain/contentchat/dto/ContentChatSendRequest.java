package com.team02.mopl.domain.contentchat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContentChatSendRequest(@NotBlank @Size(max = 500) String content) {}
