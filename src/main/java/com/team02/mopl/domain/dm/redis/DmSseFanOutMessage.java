package com.team02.mopl.domain.dm.redis;

import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import java.util.UUID;

public record DmSseFanOutMessage(UUID receiverId, String eventId, DirectMessageDto data) {}
