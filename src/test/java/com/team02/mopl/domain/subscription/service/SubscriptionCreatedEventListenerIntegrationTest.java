package com.team02.mopl.domain.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.domain.sse.service.SseEventService;
import com.team02.mopl.domain.subscription.event.SubscriptionCreatedEvent;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.support.IntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class SubscriptionCreatedEventListenerIntegrationTest extends IntegrationTestSupport {

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private UserRepository userRepository;

  @Autowired private NotificationRepository notificationRepository;

  @MockitoBean private SseEventService sseEventService;

  @Test
  @DisplayName("구독 생성 이벤트 커밋 후 PLAYLIST_SUBSCRIBED 알림이 새 트랜잭션으로 저장된다")
  void onSubscriptionCreated_afterCommit_persistsNotification() {
    User owner =
        userRepository.save(
            new User("플리소유자", uniqueEmail("owner"), "password", null, Role.USER, false));
    User subscriber =
        userRepository.save(
            new User("구독자", uniqueEmail("subscriber"), "password", null, Role.USER, false));

    TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    transactionTemplate.executeWithoutResult(
        status ->
            eventPublisher.publishEvent(
                new SubscriptionCreatedEvent(
                    subscriber.getId(), subscriber.getName(), owner.getId(), "QA 플레이리스트")));

    List<Notification> notifications = notificationRepository.findAll();

    assertThat(notifications).hasSize(1);

    Notification notification = notifications.get(0);
    assertThat(notification.getReceiver().getId()).isEqualTo(owner.getId());
    assertThat(notification.getNotificationType()).isEqualTo(NotificationType.PLAYLIST_SUBSCRIBED);
    assertThat(notification.getTitle()).isEqualTo("플레이리스트 구독 알림");
    assertThat(notification.getContent()).contains("구독자");
    assertThat(notification.getContent()).contains("QA 플레이리스트");
    assertThat(notification.getDeletedAt()).isNull();
  }

  private String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@mopl.io";
  }
}
