package com.team02.mopl.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class NotificationRepositoryTest extends RepositoryTestSupport {

  @Autowired private NotificationRepository notificationRepository;

  @Autowired private EntityManager em;

  private User receiver;
  private User otherReceiver;

  @BeforeEach
  void setUp() {
    receiver = saveUser("receiver");
    otherReceiver = saveUser("other-receiver");
  }

  @Test
  @DisplayName("첫 페이지는 수신자의 삭제되지 않은 알림을 최신순으로 조회한다")
  void findNotificationsByCursor_firstPage_returnsActiveNotificationsByCreatedAtDesc() {
    Notification oldNotification =
        saveNotification(receiver, "오래된 알림", Instant.parse("2026-06-29T01:00:00Z"));
    Notification newNotification =
        saveNotification(receiver, "최신 알림", Instant.parse("2026-06-29T03:00:00Z"));
    Notification deletedNotification =
        saveNotification(receiver, "삭제된 알림", Instant.parse("2026-06-29T04:00:00Z"));
    saveNotification(otherReceiver, "다른 사용자 알림", Instant.parse("2026-06-29T05:00:00Z"));

    deletedNotification.delete();
    em.flush();
    em.clear();

    List<Notification> result =
        notificationRepository.findNotificationsByCursor(
            receiver.getId(),
            NotificationSortBy.createdAt,
            SortDirection.DESCENDING,
            null,
            null,
            10);

    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(newNotification.getId(), oldNotification.getId());
  }

  @Test
  @DisplayName("커서가 있으면 커서 이후의 다음 페이지를 조회한다")
  void findNotificationsByCursor_withCursor_returnsNextPage() {
    Notification newest =
        saveNotification(receiver, "첫 번째 알림", Instant.parse("2026-06-29T03:00:00Z"));
    Notification middle =
        saveNotification(receiver, "두 번째 알림", Instant.parse("2026-06-29T02:00:00Z"));
    Notification oldest =
        saveNotification(receiver, "세 번째 알림", Instant.parse("2026-06-29T01:00:00Z"));

    em.clear();

    List<Notification> result =
        notificationRepository.findNotificationsByCursor(
            receiver.getId(),
            NotificationSortBy.createdAt,
            SortDirection.DESCENDING,
            newest.getCreatedAt().toString(),
            newest.getId(),
            10);

    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(middle.getId(), oldest.getId());
  }

  @Test
  @DisplayName("오름차순 요청이면 오래된 알림부터 조회한다")
  void findNotificationsByCursor_ascending_returnsOldestFirst() {
    Notification oldest =
        saveNotification(receiver, "첫 번째 알림", Instant.parse("2026-06-29T01:00:00Z"));
    Notification middle =
        saveNotification(receiver, "두 번째 알림", Instant.parse("2026-06-29T02:00:00Z"));
    Notification newest =
        saveNotification(receiver, "세 번째 알림", Instant.parse("2026-06-29T03:00:00Z"));

    em.clear();

    List<Notification> result =
        notificationRepository.findNotificationsByCursor(
            receiver.getId(),
            NotificationSortBy.createdAt,
            SortDirection.ASCENDING,
            null,
            null,
            10);

    assertThat(result)
        .extracting(Notification::getId)
        .containsExactly(oldest.getId(), middle.getId(), newest.getId());
  }

  @Test
  @DisplayName("cursor와 idAfter 중 하나만 있으면 INVALID_REQUEST 예외가 발생한다")
  void findNotificationsByCursor_partialCursor_throwsInvalidRequest() {
    assertThatThrownBy(
            () ->
                notificationRepository.findNotificationsByCursor(
                    receiver.getId(),
                    NotificationSortBy.createdAt,
                    SortDirection.DESCENDING,
                    "2026-06-29T00:00:00Z",
                    null,
                    10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

    assertThatThrownBy(
            () ->
                notificationRepository.findNotificationsByCursor(
                    receiver.getId(),
                    NotificationSortBy.createdAt,
                    SortDirection.DESCENDING,
                    null,
                    UUID.randomUUID(),
                    10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
  }

  @Test
  @DisplayName("잘못된 cursor 형식이면 INVALID_REQUEST 예외가 발생한다")
  void findNotificationsByCursor_invalidCursor_throwsInvalidRequest() {
    assertThatThrownBy(
            () ->
                notificationRepository.findNotificationsByCursor(
                    receiver.getId(),
                    NotificationSortBy.createdAt,
                    SortDirection.DESCENDING,
                    "not-an-instant",
                    UUID.randomUUID(),
                    10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
  }

  @Test
  @DisplayName("수신자의 삭제되지 않은 알림 개수를 조회한다")
  void countByReceiver_IdAndDeletedAtIsNull_countsActiveNotifications() {
    saveNotification(receiver, "첫 번째 알림", Instant.parse("2026-06-29T01:00:00Z"));
    Notification deletedNotification =
        saveNotification(receiver, "삭제된 알림", Instant.parse("2026-06-29T02:00:00Z"));
    saveNotification(otherReceiver, "다른 사용자 알림", Instant.parse("2026-06-29T03:00:00Z"));

    deletedNotification.delete();
    em.flush();

    long count = notificationRepository.countByReceiver_IdAndDeletedAtIsNull(receiver.getId());

    assertThat(count).isEqualTo(1L);
  }

  private User saveUser(String name) {
    UUID id = UUID.randomUUID();
    User user = new User(name, name + "-" + id + "@test.com", "password1!", null, Role.USER, false);

    em.persist(user);
    em.flush();

    return user;
  }

  private Notification saveNotification(User receiver, String title, Instant createdAt) {
    Notification notification =
        notificationRepository.save(
            new Notification(
                receiver, title, "알림 내용", NotificationLevel.INFO, NotificationType.USER_FOLLOWED));

    em.flush();

    em.createQuery("UPDATE Notification n SET n.createdAt = :createdAt WHERE n.id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", notification.getId())
        .executeUpdate();

    em.flush();
    em.refresh(notification);

    return notification;
  }
}
