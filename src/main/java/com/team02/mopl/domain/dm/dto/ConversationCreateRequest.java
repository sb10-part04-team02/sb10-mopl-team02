package com.team02.mopl.domain.dm.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConversationCreateRequest(@NotNull UUID withUserId) {}
