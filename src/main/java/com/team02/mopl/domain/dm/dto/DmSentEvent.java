package com.team02.mopl.domain.dm.dto;

import java.util.UUID;

public record DmSentEvent(UUID receiverUserId, String eventId, DirectMessageDto dto) {}
