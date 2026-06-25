package com.team02.mopl.domain.notification.service;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
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
  private final UserRepository userRepository;

  @Transactional
  public NotificationDto createNotification(@Valid NotificationCreateCommand command) {
    User receiver = getActiveUser(command.receiverId());

    Notification notification =
        new Notification(
            receiver,
            command.title(),
            command.content(),
            command.level(),
            command.notificationType());

    return NotificationDto.from(notificationRepository.save(notification));
  }

  // TODO: 커서 페이지네이션 구현 후 수정 예정
  public List<NotificationDto> getNotifications(UUID receiverId) {
    return notificationRepository
        .findByReceiver_IdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(receiverId)
        .stream()
        .map(NotificationDto::from)
        .toList();
  }

  // 읽음 처리
  @Transactional
  public void markAsRead(UUID notificationId, UUID receiverId) {
    Notification notification =
        notificationRepository
            .findByIdAndDeletedAtIsNull(notificationId)
            .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

    validateOwner(notification, receiverId);

    notification.delete();
  }

  private User getActiveUser(UUID userId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  private void validateOwner(Notification notification, UUID receiverId) {
    if (!notification.getReceiver().getId().equals(receiverId)) {
      throw new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN);
    }
  }
}
