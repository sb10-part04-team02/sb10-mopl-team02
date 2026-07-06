package com.team02.mopl.domain.notification.service;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.dto.NotificationSearchRequest;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.domain.notification.exception.NotificationForbiddenException;
import com.team02.mopl.domain.notification.exception.NotificationNotFoundException;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.domain.notification.util.NotificationCursorConverter;
import com.team02.mopl.domain.sse.service.SseEventService;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

  private static final String NOTIFICATION_EVENT_NAME = "notifications";

  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final SseEventService sseEventService;

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

    Notification savedNotification = notificationRepository.save(notification);
    NotificationDto notificationDto = NotificationDto.from(savedNotification);

    sendNotificationAfterCommit(notificationDto);

    return notificationDto;
  }

  public CursorResponse<NotificationDto> getNotifications(
      UUID receiverId, NotificationSearchRequest request) {
    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction = CursorPageRequest.normalizeSortDirection(request.sortDirection());
    NotificationSortBy sortBy =
        request.sortBy() != null ? request.sortBy() : NotificationSortBy.createdAt;

    if (!CursorPageRequest.isValidCursorCombo(request.cursor(), request.idAfter())) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    Instant cursor = NotificationCursorConverter.toSortKey(sortBy, request.cursor());

    List<Notification> notifications =
        notificationRepository.findNotificationsByCursor(
            receiverId, sortBy, direction, cursor, request.idAfter(), limit + 1);

    boolean hasNext = notifications.size() > limit;
    List<Notification> page = hasNext ? notifications.subList(0, limit) : notifications;

    List<NotificationDto> data = page.stream().map(NotificationDto::from).toList();
    long totalCount = notificationRepository.countByReceiver_IdAndDeletedAtIsNull(receiverId);

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      Notification last = page.get(page.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy.name(), direction.name());
  }

  // 읽음 처리
  @Transactional
  public void markAsRead(UUID notificationId, UUID receiverId) {
    Notification notification =
        notificationRepository
            .findByIdAndDeletedAtIsNull(notificationId)
            .orElseThrow(NotificationNotFoundException::new);

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
      throw new NotificationForbiddenException();
    }
  }

  // 알림 저장 직후, 트랜잭션 안에서 SSE 전송으로
  private void sendNotificationAfterCommit(NotificationDto notificationDto) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      sendNotification(notificationDto);
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            sendNotification(notificationDto);
          }
        });
  }

  private void sendNotification(NotificationDto notificationDto) {
    try {
      sseEventService.send(
          notificationDto.receiverId(),
          NOTIFICATION_EVENT_NAME,
          notificationDto.id().toString(),
          notificationDto);
    } catch (RuntimeException e) {
      log.warn(
          "알림 SSE event 전송 실패. notificationId={}, receiverId={}",
          notificationDto.id(),
          notificationDto.receiverId(),
          e);
    }
  }
}
