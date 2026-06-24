package com.team02.mopl.domain.notification.service;

import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
  private final NotificationRepository notificationRepository;

  public List<NotificationDto> getNotifications(UUID receiverId) {
    return notificationRepository
        .findByReceiverIdAndDeletedAtIsNullOrderByCreatedAtDesc(receiverId)
        .stream()
        .map(NotificationDto::from)
        .toList();
  }

  @Transactional
  public void deleteNotification(UUID notificationId, UUID receiverId) {
    Notification notification =
        notificationRepository
            .findByIdAndReceiverIdAndDeletedAtIsNull(notificationId, receiverId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

    notification.delete();
  }
}
