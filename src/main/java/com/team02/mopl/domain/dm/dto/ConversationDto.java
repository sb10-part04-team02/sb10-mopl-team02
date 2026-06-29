package com.team02.mopl.domain.dm.dto;

import com.team02.mopl.domain.user.dto.UserSummary;
import java.util.UUID;

public record ConversationDto(
    UUID id, UserSummary with, DirectMessageDto lastMessage, boolean hasUnread) {}
