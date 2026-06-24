package com.team02.mopl.domain.notification.dto;

import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level) {

  public static NotificationDto from(Notification notification) {
    return new NotificationDto(
        notification.getId(),
        notification.getCreatedAt(),
        notification.getReceiverId(),
        notification.getTitle(),
        notification.getContent(),
        notification.getLevel());
  }
}
