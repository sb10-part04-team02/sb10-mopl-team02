package com.team02.mopl.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.follow.event.FollowCreatedEvent;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class FollowCreatedEventListenerTest {

  @Mock private NotificationService notificationService;

  @InjectMocks private FollowCreatedEventListener listener;

  @Test
  @DisplayName("팔로우 생성 이벤트를 수신하면 팔로우 대상에게 알림을 생성한다")
  void onFollowCreated_createsUserFollowedNotification() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreatedEvent event = new FollowCreatedEvent(followerId, "팔로워", followeeId);

    listener.onFollowCreated(event);

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);

    then(notificationService).should().createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();

    assertThat(command.receiverId()).isEqualTo(followeeId);
    assertThat(command.title()).isEqualTo("새 팔로워 알림");
    assertThat(command.content()).isEqualTo("팔로워님이 팔로우했습니다.");
    assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(command.notificationType()).isEqualTo(NotificationType.USER_FOLLOWED);
  }

  @Test
  @DisplayName("팔로우 알림 생성에 실패해도 예외를 전파하지 않는다")
  void onFollowCreated_notificationFailure_doesNotThrow() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    FollowCreatedEvent event = new FollowCreatedEvent(followerId, "팔로워", followeeId);

    given(notificationService.createNotification(any()))
        .willThrow(new RuntimeException("notification failed"));

    listener.onFollowCreated(event);

    then(notificationService).should().createNotification(any());
  }

  @Test
  @DisplayName("팔로우 생성 이벤트 리스너는 알림 저장을 새 트랜잭션에서 처리한다")
  void onFollowCreated_hasRequiresNewTransaction() throws Exception {
    Method method =
        FollowCreatedEventListener.class.getMethod("onFollowCreated", FollowCreatedEvent.class);

    Transactional transactional = method.getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }
}
