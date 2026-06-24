package com.team02.mopl.domain.notification.dto;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import java.util.UUID;

public record NotificationCreateCommand(
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level,
    NotificationType notificationType) {}
