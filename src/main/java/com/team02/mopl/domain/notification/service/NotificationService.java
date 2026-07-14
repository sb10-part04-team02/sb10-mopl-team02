package com.team02.mopl.domain.notification.service;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.dto.NotificationSearchRequest;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.domain.notification.exception.NotificationForbiddenException;
import com.team02.mopl.domain.notification.exception.NotificationNotFoundException;
import com.team02.mopl.domain.notification.redis.NotificationSseFanOutPublisher;
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
import com.team02.mopl.global.exception.InvalidCursorRequestException;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
  private final NotificationSseFanOutPublisher notificationSseFanOutPublisher;
  private final SseEventService sseEventService;

  @Transactional
  public NotificationDto createNotification(@Valid NotificationCreateCommand command) {
    // Kafka 재소비로 동일 메시지가 다시 들어오면 dedupKey로 존재 검사해 중복 저장을 막는다
    if (command.dedupKey() != null
        && notificationRepository.existsByReceiver_IdAndDedupKey(
            command.receiverId(), command.dedupKey())) {
      log.debug(
          "중복 알림 감지, 저장 생략. receiverId={}, dedupKey={}", command.receiverId(), command.dedupKey());
      return null;
    }

    User receiver = getActiveUser(command.receiverId());

    Notification notification =
        new Notification(
            receiver,
            command.title(),
            command.content(),
            command.level(),
            command.notificationType(),
            command.dedupKey());

    Notification savedNotification;
    try {
      savedNotification = notificationRepository.saveAndFlush(notification);
    } catch (DataIntegrityViolationException e) {
      // 존재 검사 이후 동시 재소비 레이스로 UNIQUE 제약에 걸린 경우, 이미 저장된 것으로 간주하고 스킵 (최종 방어선)
      log.debug(
          "중복 알림 저장 충돌, 저장 생략. receiverId={}, dedupKey={}",
          command.receiverId(),
          command.dedupKey());
      return null;
    }

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
      throw new InvalidCursorRequestException();
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

  // 알림 재연결
  public void resendNotificationsAfter(UUID receiverId, UUID lastNotificationId) {
    Notification lastNotification =
        notificationRepository
            .findByIdAndReceiver_Id(lastNotificationId, receiverId)
            .orElseThrow(NotificationNotFoundException::new);

    List<NotificationDto> missedNotifications =
        notificationRepository
            .findUnreadNotificationsAfter(
                receiverId, lastNotification.getCreatedAt(), lastNotificationId)
            .stream()
            .map(NotificationDto::from)
            .toList();

    for (NotificationDto notificationDto : missedNotifications) {
      resendLocal(notificationDto);
    }
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
      notificationSseFanOutPublisher.publish(notificationDto);
    } catch (RuntimeException e) {
      log.warn(
          "알림 Redis Pub/Sub fan-out 발행 실패. notificationId={}, receiverId={}",
          notificationDto.id(),
          notificationDto.receiverId(),
          e);
    }
  }

  // 재연결 복구는 방금 이 인스턴스에 연결된 emitter가 대상이므로 fan-out 없이 로컬로만 전송
  private void resendLocal(NotificationDto notificationDto) {
    try {
      sseEventService.send(
          notificationDto.receiverId(),
          NOTIFICATION_EVENT_NAME,
          notificationDto.id().toString(),
          notificationDto);
    } catch (RuntimeException e) {
      log.warn(
          "알림 SSE 복구 전송 실패. notificationId={}, receiverId={}",
          notificationDto.id(),
          notificationDto.receiverId(),
          e);
    }
  }
}
