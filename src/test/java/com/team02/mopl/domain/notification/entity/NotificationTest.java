package com.team02.mopl.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class NotificationTest {
  @Test
  @DisplayName("알림을 생성하면 필드가 초기화된다")
  void constructor_initializesFields() {
    User receiver = createUser();

    Notification notification =
        new Notification(
            receiver, "알림 제목", "알림 내용", NotificationLevel.WARNING, NotificationType.USER_FOLLOWED);

    assertThat(notification.getReceiver()).isSameAs(receiver);
    assertThat(notification.getTitle()).isEqualTo("알림 제목");
    assertThat(notification.getContent()).isEqualTo("알림 내용");
    assertThat(notification.getLevel()).isEqualTo(NotificationLevel.WARNING);
    assertThat(notification.getNotificationType()).isEqualTo(NotificationType.USER_FOLLOWED);
  }

  @Test
  @DisplayName("알림 수준이 null이면 INFO를 기본값으로 사용한다")
  void constructor_whenLevelIsNull_defaultsToInfo() {
    User receiver = createUser();

    Notification notification =
        new Notification(receiver, "알림 제목", "알림 내용", null, NotificationType.USER_FOLLOWED);

    assertThat(notification.getLevel()).isEqualTo(NotificationLevel.INFO);
  }

  @Test
  @DisplayName("수신자가 null이면 예외가 발생한다")
  void constructor_whenReceiverIsNull_throwsException() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Notification(
                    null,
                    "알림 제목",
                    "알림 내용",
                    NotificationLevel.INFO,
                    NotificationType.USER_FOLLOWED));
  }

  @Test
  @DisplayName("제목이 null이면 예외가 발생한다")
  void constructor_whenTitleIsNull_throwsException() {
    User receiver = createUser();

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Notification(
                    receiver,
                    null,
                    "알림 내용",
                    NotificationLevel.INFO,
                    NotificationType.USER_FOLLOWED));
  }

  @Test
  @DisplayName("내용이 null이면 예외가 발생한다")
  void constructor_whenContentIsNull_throwsException() {
    User receiver = createUser();

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Notification(
                    receiver,
                    "알림 제목",
                    null,
                    NotificationLevel.INFO,
                    NotificationType.USER_FOLLOWED));
  }

  @Test
  @DisplayName("알림 타입이 null이면 예외가 발생한다")
  void constructor_whenNotificationTypeIsNull_throwsException() {
    User receiver = createUser();

    assertThatNullPointerException()
        .isThrownBy(
            () -> new Notification(receiver, "알림 제목", "알림 내용", NotificationLevel.INFO, null));
  }

  @Test
  @DisplayName("알림을 삭제하면 deletedAt이 설정된다")
  void delete_setsDeletedAt() {
    Notification notification =
        new Notification(
            createUser(), "알림 제목", "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED);

    notification.delete();

    assertThat(notification.isDeleted()).isTrue();
    assertThat(notification.getDeletedAt()).isNotNull();
  }

  private User createUser() {
    return new User("사용자", "user@test.com", "password", null, Role.USER, false);
  }
}
