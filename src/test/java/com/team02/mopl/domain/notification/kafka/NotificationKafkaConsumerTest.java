package com.team02.mopl.domain.notification.kafka;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

  @Mock private NotificationService notificationService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("알림 Kafka 메시지를 수신하면 NotificationService로 알림을 생성한다")
  void consume_success() throws Exception {
    // given
    NotificationKafkaConsumer consumer =
        new NotificationKafkaConsumer(objectMapper, notificationService);

    UUID receiverId = UUID.randomUUID();
    String dedupKey = "USER_FOLLOWED:" + receiverId + ":" + UUID.randomUUID();
    NotificationKafkaMessage message =
        new NotificationKafkaMessage(
            receiverId,
            "새 팔로워 알림",
            "팔로워님이 팔로우했습니다.",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED,
            dedupKey);

    String payload = objectMapper.writeValueAsString(message);

    // when
    consumer.consume(payload);

    // then
    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);

    then(notificationService).should().createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();

    org.assertj.core.api.Assertions.assertThat(command.receiverId()).isEqualTo(receiverId);
    org.assertj.core.api.Assertions.assertThat(command.title()).isEqualTo("새 팔로워 알림");
    org.assertj.core.api.Assertions.assertThat(command.content()).isEqualTo("팔로워님이 팔로우했습니다.");
    org.assertj.core.api.Assertions.assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    org.assertj.core.api.Assertions.assertThat(command.notificationType())
        .isEqualTo(NotificationType.USER_FOLLOWED);
    org.assertj.core.api.Assertions.assertThat(command.dedupKey()).isEqualTo(dedupKey);
  }

  @Test
  @DisplayName("역직렬화할 수 없는 Kafka 메시지는 예외를 전파하지 않고 무시한다")
  void consume_invalidPayload_doesNotThrow() {
    // given
    NotificationKafkaConsumer consumer =
        new NotificationKafkaConsumer(objectMapper, notificationService);

    String invalidPayload = "{ invalid-json";

    // when & then
    assertThatCode(() -> consumer.consume(invalidPayload)).doesNotThrowAnyException();
    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("알림 생성 처리 중 예외가 발생하면 Kafka 재시도를 위해 예외를 전파한다")
  void consume_notificationFailure_throwsException() throws Exception {
    // given
    NotificationKafkaConsumer consumer =
        new NotificationKafkaConsumer(objectMapper, notificationService);

    NotificationKafkaMessage message =
        new NotificationKafkaMessage(
            UUID.randomUUID(),
            "새 팔로워 알림",
            "팔로워님이 팔로우했습니다.",
            NotificationLevel.INFO,
            NotificationType.USER_FOLLOWED,
            null);

    String payload = objectMapper.writeValueAsString(message);

    org.mockito.BDDMockito.given(
            notificationService.createNotification(org.mockito.ArgumentMatchers.any()))
        .willThrow(new RuntimeException("notification failed"));

    // when & then
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> consumer.consume(payload))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("notification failed");

    then(notificationService).should().createNotification(org.mockito.ArgumentMatchers.any());
  }
}
