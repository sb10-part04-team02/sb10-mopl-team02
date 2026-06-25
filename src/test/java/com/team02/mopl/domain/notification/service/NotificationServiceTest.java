package com.team02.mopl.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock private NotificationRepository notificationRepository;

  @Mock private UserRepository userRepository;

  @InjectMocks private NotificationService notificationService;

  @Test
  @DisplayName("알림 생성 요청을 저장하고 응답 DTO를 반환한다")
  void createNotification_success() {
    UUID receiverId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.of(receiver));
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    NotificationDto result = notificationService.createNotification(command);

    assertThat(result.receiverId()).isEqualTo(receiverId);
    assertThat(result.title()).isEqualTo("알림 제목");
    assertThat(result.content()).isEqualTo("알림 내용");
    assertThat(result.level()).isEqualTo(NotificationLevel.INFO);

    verify(userRepository).findByIdAndDeletedAtIsNull(receiverId);
    verify(notificationRepository).save(any(Notification.class));
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
  @DisplayName("알림 생성 시 level이 null이면 INFO로 기본 설정된다")
  void createNotification_levelNull_defaultsToInfo() {
    UUID receiverId = UUID.randomUUID();
    User receiver = mockUser(receiverId);
    NotificationCreateCommand command =
        new NotificationCreateCommand(
            receiverId, "알림 제목", "알림 내용", null, NotificationType.USER_FOLLOWED);

    given(userRepository.findByIdAndDeletedAtIsNull(receiverId)).willReturn(Optional.of(receiver));
    given(notificationRepository.save(any(Notification.class)))
        .willAnswer(invocation -> invocation.getArgument(0));

    NotificationDto result = notificationService.createNotification(command);

    assertThat(result.level()).isEqualTo(NotificationLevel.INFO);

    verify(notificationRepository).save(any(Notification.class));
  }

  @Test
  @DisplayName("수신자의 활성 알림 목록을 조회한다")
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

    given(
            notificationRepository.findByReceiver_IdAndDeletedAtIsNullOrderByCreatedAtDesc(
                receiverId))
        .willReturn(List.of(notification1, notification2));

    List<NotificationDto> result = notificationService.getNotifications(receiverId);

    assertThat(result).hasSize(2);
    assertThat(result).extracting(NotificationDto::title).containsExactly("첫 번째 알림", "두 번째 알림");
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

  private User mockUser(UUID id) {
    User user = mock(User.class);
    given(user.getId()).willReturn(id);
    return user;
  }
}
