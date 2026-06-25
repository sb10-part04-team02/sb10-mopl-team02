package com.team02.mopl.domain.notification.repository;

import com.team02.mopl.domain.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  List<Notification> findByReceiverIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(UUID receiverId);

  Optional<Notification> findByIdAndDeletedAtIsNull(UUID notificationId);
}
