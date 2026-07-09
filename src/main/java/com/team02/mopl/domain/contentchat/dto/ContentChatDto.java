package com.team02.mopl.domain.contentchat.dto;

import com.team02.mopl.domain.user.dto.UserSummary;

public record ContentChatDto(UserSummary sender, String content) {}
