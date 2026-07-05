package com.team02.mopl.domain.notification.repository;

import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.global.enums.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationRepositoryCustom {
  List<Notification> findNotificationsByCursor(
      UUID receiverId,
      NotificationSortBy sortBy,
      SortDirection direction,
      Instant cursor,
      UUID idAfter,
      int limit);
}
