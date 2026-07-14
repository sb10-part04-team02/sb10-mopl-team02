package com.team02.mopl.domain.notification.redis;

import com.team02.mopl.domain.notification.dto.NotificationDto;
import java.util.UUID;

public record NotificationSseFanOutMessage(
    UUID receiverId, String eventName, String eventId, NotificationDto data) {}
