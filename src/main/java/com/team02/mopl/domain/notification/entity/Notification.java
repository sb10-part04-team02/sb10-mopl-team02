package com.team02.mopl.domain.notification.entity;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

  @Column(name = "receiver_id", nullable = false)
  private UUID receiverId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 255)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private NotificationLevel level = NotificationLevel.INFO;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 30)
  private NotificationType notificationType;

  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Column(name = "read_at")
  private Instant readAt;

  public Notification(
      UUID receiverId,
      String title,
      String content,
      NotificationLevel level,
      NotificationType notificationType) {
    this.receiverId = receiverId;
    this.title = title;
    this.content = content;
    this.level = level;
    this.notificationType = notificationType;
  }

  public void markAsRead() {
    if (this.read) {
      return;
    }

    this.read = true;
    this.readAt = Instant.now();
  }
}
