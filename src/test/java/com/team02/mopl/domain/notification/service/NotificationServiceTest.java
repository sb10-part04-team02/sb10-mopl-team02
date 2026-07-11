package com.team02.mopl.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.dto.NotificationSearchRequest;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.domain.sse.service.SseEventService;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;

  @Mock private UserRepository userRepository;

  @Mock private SseEventService sseEventService;

  @InjectMocks private NotificationService notificationService;

  @Test
  @DisplayName("알림 생성 요청을 저장하고 응답 DTO를 반환한다")
  void createNotification_success() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.of(receiver));
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(
            invocation -> {
              Notification notification = invocation.getArgument(0);
              ReflectionTestUtils.setField(notification, "id", notificationId);
              return notification;
            });

    NotificationDto result = notificationService.createNotification(command);

    assertThat(result.id()).isEqualTo(notificationId);
    assertThat(result.receiverId()).isEqualTo(receiverId);
    assertThat(result.title()).isEqualTo("알림 제목");
    assertThat(result.content()).isEqualTo("알림 내용");
    assertThat(result.level()).isEqualTo(NotificationLevel.INFO);

    verify(userRepository).findByIdAndDeletedAtIsNull(receiverId);
    verify(notificationRepository).save(any(Notification.class));
  }

  @Test
  @DisplayName("알림 생성 후 notifications SSE 이벤트를 전송한다")
  void createNotification_sendsSseEvent() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.of(receiver));
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(
            invocation -> {
              Notification notification = invocation.getArgument(0);
              ReflectionTestUtils.setField(notification, "id", notificationId);
              return notification;
            });

    NotificationDto result = notificationService.createNotification(command);

    verify(sseEventService)
        .send(eq(receiverId), eq("notifications"), eq(notificationId.toString()), eq(result));
  }

  @Test
  @DisplayName("존재하지 않는 수신자에게 알림을 생성하면 USER_NOT_FOUND 예외가 발생한다")
  void createNotification_receiverNotFound_throwsException() {
    UUID receiverId = UUID.randomUUID();
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.createNotification(command))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
  }

  @Test
  @DisplayName("알림 생성 시 level이 null이면 INFO로 기본 설정한다")
  void createNotification_levelNull_defaultsToInfo() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", null, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.of(receiver));
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(
            invocation -> {
              Notification notification = invocation.getArgument(0);
              ReflectionTestUtils.setField(notification, "id", notificationId);
              return notification;
            });

    NotificationDto result = notificationService.createNotification(command);

    assertThat(result.level()).isEqualTo(NotificationLevel.INFO);

    verify(notificationRepository).save(any(Notification.class));
  }

  @Test
  @DisplayName("수신자의 활성 알림 목록을 커서 응답으로 조회한다")
  void getNotifications_success() {
    UUID receiverId = UUID.randomUUID();
    User receiver = mockUser(receiverId);

    Notification notification1 =
        new Notification(
            receiver, "첫 번째 알림", "첫 번째 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);
    Notification notification2 =
        new Notification(
            receiver,
            "두 번째 알림",
            "두 번째 내용",
            NotificationLevel.WARNING,
            NotificationType.ROLE_UPDATED);

    NotificationSearchRequest request = new NotificationSearchRequest(null, null, null, null, null);

    given(
            notificationRepository.findNotificationsByCursor(
                eq(receiverId),
                eq(NotificationSortBy.createdAt),
                eq(SortDirection.DESCENDING),
                eq(null),
                eq(null),
                eq(21)))
        .willReturn(List.of(notification1, notification2));
    given(notificationRepository.countByReceiver_IdAndDeletedAtIsNull(receiverId)).willReturn(2L);

    CursorResponse<NotificationDto> result =
        notificationService.getNotifications(receiverId, request);

    assertThat(result.data()).hasSize(2);
    assertThat(result.data())
        .extracting(NotificationDto::title)
        .containsExactly("첫 번째 알림", "두 번째 알림");
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.nextIdAfter()).isNull();
    assertThat(result.totalCount()).isEqualTo(2L);
    assertThat(result.sortBy()).isEqualTo("createdAt");
    assertThat(result.sortDirection()).isEqualTo("DESCENDING");
  }

  @Test
  @DisplayName("알림 목록 조회 시 limit보다 하나 더 조회해 다음 페이지 여부를 판단한다")
  void getNotifications_hasNext_success() {
    UUID receiverId = UUID.randomUUID();
    User receiver = mockUser(receiverId);

    Notification notification1 =
        createNotificationWithIdAndCreatedAt(
            receiver, UUID.randomUUID(), Instant.parse("2026-06-29T03:00:00Z"), "첫 번째 알림");
    Notification notification2 =
        createNotificationWithIdAndCreatedAt(
            receiver, UUID.randomUUID(), Instant.parse("2026-06-29T02:00:00Z"), "두 번째 알림");
    Notification notification3 =
        createNotificationWithIdAndCreatedAt(
            receiver, UUID.randomUUID(), Instant.parse("2026-06-29T01:00:00Z"), "세 번째 알림");

    NotificationSearchRequest request =
        new NotificationSearchRequest(
            null, null, 2, SortDirection.DESCENDING, NotificationSortBy.createdAt);

    given(
            notificationRepository.findNotificationsByCursor(
                eq(receiverId),
                eq(NotificationSortBy.createdAt),
                eq(SortDirection.DESCENDING),
                eq(null),
                eq(null),
                eq(3)))
        .willReturn(List.of(notification1, notification2, notification3));
    given(notificationRepository.countByReceiver_IdAndDeletedAtIsNull(receiverId)).willReturn(3L);

    CursorResponse<NotificationDto> result =
        notificationService.getNotifications(receiverId, request);

    assertThat(result.data()).hasSize(2);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo("2026-06-29T02:00:00Z");
    assertThat(result.nextIdAfter()).isEqualTo(notification2.getId());
    assertThat(result.totalCount()).isEqualTo(3L);
  }

  @Test
  @DisplayName("cursor와 idAfter 중 하나만 있으면 INVALID_REQUEST 예외가 발생한다")
  void getNotifications_partialCursor_throwsInvalidRequest() {
    UUID receiverId = UUID.randomUUID();

    NotificationSearchRequest cursorOnly =
        new NotificationSearchRequest("2026-06-29T00:00:00Z", null, null, null, null);
    assertThatThrownBy(() -> notificationService.getNotifications(receiverId, cursorOnly))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

    NotificationSearchRequest idAfterOnly =
        new NotificationSearchRequest(null, UUID.randomUUID(), null, null, null);
    assertThatThrownBy(() -> notificationService.getNotifications(receiverId, idAfterOnly))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
  }

  @Test
  @DisplayName("잘못된 cursor 형식이면 INVALID_CURSOR 예외가 발생한다")
  void getNotifications_invalidCursor_throwsInvalidCursor() {
    UUID receiverId = UUID.randomUUID();

    NotificationSearchRequest request =
        new NotificationSearchRequest("not-an-instant", UUID.randomUUID(), null, null, null);

    assertThatThrownBy(() -> notificationService.getNotifications(receiverId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR));
  }

  @Test
  @DisplayName("본인의 알림을 읽음 처리하면 deletedAt이 설정된다")
  void markAsRead_success() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    Notification notification =
        new Notification(
            receiver, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(notificationRepository.findByIdAndDeletedAtIsNull(notificationId))
        .willReturn(Optional.of(notification));

    notificationService.markAsRead(notificationId, receiverId);

    assertThat(notification.getDeletedAt()).isNotNull();
  }

  @Test
  @DisplayName("존재하지 않는 알림을 읽음 처리하면 NOTIFICATION_NOT_FOUND 예외가 발생한다")
  void markAsRead_notFound_throwsException() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    given(notificationRepository.findByIdAndDeletedAtIsNull(notificationId))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.markAsRead(notificationId, receiverId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND));
  }

  @Test
  @DisplayName("다른 사용자의 알림을 읽음 처리하면 NOTIFICATION_FORBIDDEN 예외가 발생한다")
  void markAsRead_differentReceiver_throwsException() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    User owner = mockUser(ownerId);
    Notification notification =
        new Notification(
            owner, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(notificationRepository.findByIdAndDeletedAtIsNull(notificationId))
        .willReturn(Optional.of(notification));

    assertThatThrownBy(() -> notificationService.markAsRead(notificationId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTIFICATION_FORBIDDEN));
  }

  @Test
  @DisplayName("트랜잭션 동기화가 활성화되어 있으면 커밋 이후 SSE 이벤트를 전송한다")
  void createNotification_transactionActive_sendsSseAfterCommit() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.of(receiver));
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(
            invocation -> {
              Notification notification = invocation.getArgument(0);
              ReflectionTestUtils.setField(notification, "id", notificationId);
              return notification;
            });

    TransactionSynchronizationManager.initSynchronization();

    try {
      NotificationDto result = notificationService.createNotification(command);

      verify(sseEventService, never())
          .send(any(UUID.class), any(String.class), any(String.class), any(NotificationDto.class));

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      verify(sseEventService)
          .send(eq(receiverId), eq("notifications"), eq(notificationId.toString()), eq(result));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("SSE 전송에 실패해도 알림 생성 결과를 반환한다")
  void createNotification_sseSendFails_stillReturnsNotificationDto() {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.of(receiver));
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(
            invocation -> {
              Notification notification = invocation.getArgument(0);
              ReflectionTestUtils.setField(notification, "id", notificationId);
              return notification;
            });

    doThrow(new RuntimeException("SSE 전송 실패"))
        .when(sseEventService)
        .send(
            eq(receiverId),
            eq("notifications"),
            eq(notificationId.toString()),
            any(NotificationDto.class));

    NotificationDto result = notificationService.createNotification(command);

    assertThat(result.id()).isEqualTo(notificationId);
    assertThat(result.receiverId()).isEqualTo(receiverId);
    assertThat(result.title()).isEqualTo("알림 제목");

    verify(notificationRepository).save(any(Notification.class));
    verify(sseEventService)
        .send(
            eq(receiverId),
            eq("notifications"),
            eq(notificationId.toString()),
            any(NotificationDto.class));
  }

  @Test
  @DisplayName("Last-Event-ID 이후 알림들을 SSE로 재전송한다")
  void resendNotificationsAfter_sendsMissedNotifications() {
    // given
    UUID receiverId = UUID.randomUUID();
    UUID lastNotificationId = UUID.randomUUID();

    User receiver = new User("수신자", "receiver@mopl.io", "password", null, Role.USER, false);

    Notification lastNotification =
        new Notification(
            receiver,
            "마지막 수신 알림",
            "마지막 수신 알림 내용",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED);

    Notification missedNotification1 =
        new Notification(
            receiver,
            "누락 알림 1",
            "누락 알림 내용 1",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED);

    Notification missedNotification2 =
        new Notification(
            receiver,
            "누락 알림 2",
            "누락 알림 내용 2",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED);

    ReflectionTestUtils.setField(receiver, "id", receiverId);
    ReflectionTestUtils.setField(lastNotification, "id", lastNotificationId);
    ReflectionTestUtils.setField(
        lastNotification, "createdAt", Instant.parse("2026-07-11T00:00:00Z"));
    ReflectionTestUtils.setField(missedNotification1, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(
        missedNotification1, "createdAt", Instant.parse("2026-07-11T00:01:00Z"));
    ReflectionTestUtils.setField(missedNotification2, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(
        missedNotification2, "createdAt", Instant.parse("2026-07-11T00:02:00Z"));

    given(notificationRepository.findByIdAndReceiver_Id(lastNotificationId, receiverId))
        .willReturn(Optional.of(lastNotification));
    given(
            notificationRepository.findUnreadNotificationsAfter(
                receiverId, lastNotification.getCreatedAt()))
        .willReturn(List.of(missedNotification1, missedNotification2));

    // when
    notificationService.resendNotificationsAfter(receiverId, lastNotificationId);

    // then
    then(sseEventService)
        .should(times(1))
        .send(
            eq(receiverId),
            eq("notifications"),
            eq(missedNotification1.getId().toString()),
            any(NotificationDto.class));

    then(sseEventService)
        .should(times(1))
        .send(
            eq(receiverId),
            eq("notifications"),
            eq(missedNotification2.getId().toString()),
            any(NotificationDto.class));
  }

  private Notification createNotificationWithIdAndCreatedAt(
      User receiver, UUID id, Instant createdAt, String title) {
    Notification notification =
        new Notification(
            receiver, title, "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);
    ReflectionTestUtils.setField(notification, "id", id);
    ReflectionTestUtils.setField(notification, "createdAt", createdAt);
    return notification;
  }

  private User mockUser(UUID id) {
    User user = mock(User.class);
    given(user.getId()).willReturn(id);
    return user;
  }
}
