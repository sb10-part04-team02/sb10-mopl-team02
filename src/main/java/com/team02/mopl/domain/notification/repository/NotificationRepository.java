package com.team02.mopl.domain.notification.repository;

import com.team02.mopl.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository
    extends JpaRepository<Notification, UUID>, NotificationRepositoryCustom {

  Optional<Notification> findByIdAndDeletedAtIsNull(UUID notificationId);

  // 커서 페이지네이션 totalCount용
  long countByReceiver_IdAndDeletedAtIsNull(UUID receiverId);

  Optional<Notification> findByIdAndReceiver_Id(UUID notificationId, UUID receiverId);

  @Query(
      "SELECT n FROM Notification n "
          + "WHERE n.receiver.id = :receiverId "
          + "AND n.deletedAt IS NULL "
          + "AND n.createdAt > :lastCreatedAt "
          + "ORDER BY n.createdAt ASC, n.id ASC")
  List<Notification> findUnreadNotificationsAfter(
      @Param("receiverId") UUID receiverId, @Param("lastCreatedAt") Instant lastCreatedAt);
}
