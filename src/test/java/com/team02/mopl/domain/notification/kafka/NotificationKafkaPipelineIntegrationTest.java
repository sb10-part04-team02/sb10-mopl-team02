package com.team02.mopl.domain.notification.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.redis.NotificationSseFanOutPublisher;
import com.team02.mopl.domain.notification.repository.NotificationRepository;
import com.team02.mopl.domain.subscription.event.SubscriptionCreatedEvent;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.support.TestcontainersConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 알림 Kafka 파이프라인 end-to-end 통합 테스트.
 *
 * <p>도메인 이벤트 발행 → 리스너의 Kafka 발행 → {@link NotificationKafkaConsumer} 소비 → DB 저장까지 실제 EmbeddedKafka
 * 브로커를 통해 연결되는지 검증한다. 단위 테스트는 발행 호출만 검증하므로, 토픽/키/직렬화가 실제로 맞물리는지는 이 테스트가 담당한다.
 *
 * <p>Redis Pub/Sub fan-out은 이 테스트 범위가 아니므로 {@link NotificationSseFanOutPublisher}를 목으로 대체한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EmbeddedKafka(
    partitions = 1,
    topics = NotificationKafkaTopics.NOTIFICATION_EVENTS,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class NotificationKafkaPipelineIntegrationTest {

  @Autowired private ApplicationEventPublisher eventPublisher;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private UserRepository userRepository;

  @Autowired private NotificationRepository notificationRepository;

  @Autowired private NotificationKafkaConsumer notificationKafkaConsumer;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private NotificationSseFanOutPublisher notificationSseFanOutPublisher;

  @Test
  @DisplayName("구독 생성 이벤트가 Kafka 발행·소비를 거쳐 PLAYLIST_SUBSCRIBED 알림으로 DB에 저장된다")
  void subscriptionEvent_flowsThroughKafka_andPersistsNotification() {
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

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              List<Notification> notifications =
                  notificationRepository.findAll().stream()
                      .filter(n -> n.getReceiver().getId().equals(owner.getId()))
                      .toList();
              assertThat(notifications).hasSize(1);

              Notification notification = notifications.get(0);
              assertThat(notification.getNotificationType())
                  .isEqualTo(NotificationType.PLAYLIST_SUBSCRIBED);
              assertThat(notification.getTitle()).isEqualTo("플레이리스트 구독 알림");
              assertThat(notification.getContent()).contains("구독자");
              assertThat(notification.getContent()).contains("QA 플레이리스트");
              assertThat(notification.getDeletedAt()).isNull();
            });
  }

  @Test
  @DisplayName("동일 dedupKey의 알림 메시지를 두 번 소비해도 알림은 1건만 저장된다")
  void duplicateMessage_consumedTwice_persistsSingleNotification() throws Exception {
    User receiver =
        userRepository.save(
            new User("수신자", uniqueEmail("receiver"), "password", null, Role.USER, false));

    String dedupKey = "USER_FOLLOWED:" + receiver.getId() + ":" + UUID.randomUUID();
    NotificationKafkaMessage message =
        new NotificationKafkaMessage(
            receiver.getId(),
            "새 팔로워 알림",
            "팔로워님이 팔로우했습니다.",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED,
            dedupKey);
    String payload = objectMapper.writeValueAsString(message);

    // Kafka 재소비를 시뮬레이션하기 위해 동일 payload를 두 번 소비한다
    notificationKafkaConsumer.consume(payload);
    notificationKafkaConsumer.consume(payload);

    List<Notification> notifications =
        notificationRepository.findAll().stream()
            .filter(n -> n.getReceiver().getId().equals(receiver.getId()))
            .toList();
    assertThat(notifications).hasSize(1);
    assertThat(notifications.get(0).getDedupKey()).isEqualTo(dedupKey);
  }

  private String uniqueEmail(String prefix) {
    return prefix + "-" + UUID.randomUUID() + "@mopl.io";
  }
}
