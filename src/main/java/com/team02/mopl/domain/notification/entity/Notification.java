package com.team02.mopl.domain.notification.entity;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
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

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "content", nullable = false, length = 255)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(name = "level", nullable = false, length = 10)
  private NotificationLevel level = NotificationLevel.INFO;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 30)
  private NotificationType notificationType;

  public Notification(
      UUID receiverId,
      String title,
      String content,
      NotificationLevel level,
      NotificationType notificationType) {
    this.receiverId = Objects.requireNonNull(receiverId, "receiverId는 null일 수 없습니다.");
    this.title = Objects.requireNonNull(title, "title은 null일 수 없습니다.");
    this.content = Objects.requireNonNull(content, "content는 null일 수 없습니다.");
    this.level = level == null ? NotificationLevel.INFO : level;
    this.notificationType =
        Objects.requireNonNull(notificationType, "notificationType은 null일 수 없습니다.");
  }
}
