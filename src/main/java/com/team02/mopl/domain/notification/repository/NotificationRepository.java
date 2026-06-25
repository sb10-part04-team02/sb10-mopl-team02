package com.team02.mopl.domain.notification.repository;

import com.team02.mopl.domain.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  List<Notification> findByReceiver_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID receiverId);

  Optional<Notification> findByIdAndReceiver_IdAndDeletedAtIsNull(
      UUID notificationId, UUID receiverId);
}
