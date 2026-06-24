package com.team02.mopl.domain.notification.dto;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record NotificationCreateCommand(
    @NotNull UUID receiverId,
    @NotBlank String title,
    @NotBlank String content,
    NotificationLevel level,
    @NotNull NotificationType notificationType) {}
