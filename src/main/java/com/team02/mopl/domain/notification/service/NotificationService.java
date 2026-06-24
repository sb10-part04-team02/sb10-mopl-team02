package com.team02.mopl.domain.notification.service;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private final NotificationRepository notificationRepository;

  @Transactional
  public NotificationDto createNotification(@Valid NotificationCreateCommand command) {

    Notification notification =
        new Notification(
            command.receiverId(),
            command.title(),
            command.content(),
            command.level(),
            command.notificationType());

    return NotificationDto.from(notificationRepository.save(notification));
  }

  // TODO: 커서 페이지네이션 구현 후 수정 예정
  public List<NotificationDto> getNotifications(UUID receiverId) {
    return notificationRepository
        .findByReceiverIdAndDeletedAtIsNullOrderByCreatedAtDesc(receiverId)
        .stream()
        .map(NotificationDto::from)
        .toList();
  }

  // 읽음 처리
  @Transactional
  public void markAsRead(UUID notificationId, UUID receiverId) {
    Notification notification =
        notificationRepository
            .findByIdAndReceiverIdAndDeletedAtIsNull(notificationId, receiverId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

    notification.delete();
  }

  // 요청자와 수신자 일치 확인
  private Notification getOwnedActiveNotification(UUID notificationId, UUID receiverId) {
    return notificationRepository
        .findByIdAndReceiverIdAndDeletedAtIsNull(notificationId, receiverId)
        .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
  }
}
