package com.team02.mopl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.event.PasswordUpdatedEvent;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import com.team02.mopl.domain.user.event.UserLockUpdatedEvent;
import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;
import com.team02.mopl.global.outbox.redis.entity.enums.OutboxTarget;
import com.team02.mopl.global.outbox.redis.service.RedisOutboxService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class UserEventListenerTest {

  @Mock private JwtRegistry jwtRegistry;
  @Mock private NotificationService notificationService;
  @Mock private RedisOutboxService outboxService;

  @InjectMocks private UserEventListener eventListener;

  @Nested
  class OnUserRoleUpdated {

    @Test
    @DisplayName("권한변경 이벤트가 오면 유저의 모든 리프레시 토큰을 삭제한다")
    void success_shouldRemoveAllRefreshTokens_whenRoleUpdatedEventIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      RoleUpdatedEvent event = new RoleUpdatedEvent(userId, Role.USER, Role.ADMIN);

      // when
      eventListener.onUserRoleUpdated(event);

      // then
      then(jwtRegistry).should(times(1)).deleteAllToken(userId);
    }

    @Test
    @DisplayName("권한변경 이벤트가 오면 권한 변경 알림을 생성한다")
    void success_shouldCreateRoleUpdatedNotification_whenRoleUpdatedEventIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      RoleUpdatedEvent event = new RoleUpdatedEvent(userId, Role.USER, Role.ADMIN);

      // when
      eventListener.onUserRoleUpdated(event);

      // then
      ArgumentCaptor<NotificationCreateCommand> commandCaptor =
          ArgumentCaptor.forClass(NotificationCreateCommand.class);

      then(notificationService).should(times(1)).createNotification(commandCaptor.capture());

      NotificationCreateCommand command = commandCaptor.getValue();

      assertThat(command.receiverId()).isEqualTo(userId);
      assertThat(command.title()).isEqualTo("권한 변경 알림");
      assertThat(command.content()).isEqualTo("회원님의 권한이 USER에서 ADMIN으로 변경되었습니다.");
      assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
      assertThat(command.notificationType()).isEqualTo(NotificationType.ROLE_UPDATED);
    }

    @Test
    @DisplayName("레디스에 문제가 생기면 예외를 던지지만 catch로 방어한다")
    void fail_shouldDefenceException_whenRedisIsDown() {
      // given
      UUID userId = UUID.randomUUID();
      RoleUpdatedEvent event = new RoleUpdatedEvent(userId, Role.USER, Role.ADMIN);
      willThrow(RedisConnectionFailureException.class).given(jwtRegistry).deleteAllToken(userId);

      // when & then
      assertDoesNotThrow(() -> eventListener.onUserRoleUpdated(event));
      then(jwtRegistry).should(times(1)).deleteAllToken(userId);
    }

    @Test
    @DisplayName("권한 변경 알림 생성에 실패해도 예외를 전파하지 않는다")
    void fail_shouldNotThrowException_whenNotificationCreationFails() {
      // given
      UUID userId = UUID.randomUUID();
      RoleUpdatedEvent event = new RoleUpdatedEvent(userId, Role.USER, Role.ADMIN);
      willThrow(new RuntimeException("notification failed"))
          .given(notificationService)
          .createNotification(any());

      // when & then
      assertDoesNotThrow(() -> eventListener.onUserRoleUpdated(event));
      then(jwtRegistry).should(times(1)).deleteAllToken(userId);
      then(notificationService).should(times(1)).createNotification(any());
    }

    @Test
    @DisplayName("권한변경 이벤트 리스너는 알림 저장을 새 트랜잭션에서 처리한다")
    void onUserRoleUpdated_hasRequiresNewTransaction() throws Exception {
      // given
      Method method =
          UserEventListener.class.getMethod("onUserRoleUpdated", RoleUpdatedEvent.class);

      // when
      Transactional transactional = method.getAnnotation(Transactional.class);

      // then
      assertThat(transactional).isNotNull();
      assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
  }

  @Nested
  class OnPasswordUpdated {
    @Test
    @DisplayName("패스워드 변경 이벤트가 오면 유저의 모든 리프레시 토큰을 삭제한다")
    void success_shouldRemoveAllRefreshTokens_whenPasswordUpdatedEventIsProvided() {
      // given
      UUID userId = UUID.randomUUID();
      PasswordUpdatedEvent event = new PasswordUpdatedEvent(userId);

      // when
      eventListener.onPasswordUpdated(event);

      // then
      then(jwtRegistry).should(times(1)).deleteAllRefreshToken(userId);
    }
  }

  @Nested
  class PasswordUpdatedRecover {
    @Test
    @DisplayName("Recover함수가 호출되면 outbox를 저장한다")
    void success_shouldSaveOutbox_whenRecoverMethodIsCalled() {
      // given
      UUID userId = UUID.randomUUID();
      DataAccessException e = new RedisConnectionFailureException("test");
      PasswordUpdatedEvent event = new PasswordUpdatedEvent(userId);

      // when
      assertDoesNotThrow(() -> eventListener.passwordUpdatedRecover(e, event));

      // then
      ArgumentCaptor<RedisCommandOutbox> outboxCaptor =
          ArgumentCaptor.forClass(RedisCommandOutbox.class);
      then(outboxService).should(times(1)).saveOutbox(outboxCaptor.capture());
      RedisCommandOutbox actualOutbox = outboxCaptor.getValue();

      assertThat(actualOutbox.getCommandType()).isEqualTo(CommandType.DELETE_ALL_REFRESH_TOKEN);
      assertThat(actualOutbox.getTargetId()).isEqualTo(userId);
      assertThat(actualOutbox.getTarget()).isEqualTo(OutboxTarget.USER);
    }
  }

  @Nested
  class onUserLockUpdatedEvent {

    @Test
    @DisplayName("유저잠금 이벤트가 오면 redis에서 유저를 잠금처리한다")
    void success_shouldLockUser_whenUserLockedEventIsTrue() {
      // given
      UUID userId = UUID.randomUUID();
      UserLockUpdatedEvent event = new UserLockUpdatedEvent(userId, true);

      // when
      eventListener.onUserLockUpdatedEvent(event);

      // then
      then(jwtRegistry).should(times(1)).lockUser(userId);
      then(jwtRegistry).should(never()).unlockUser(any(UUID.class));
    }

    @Test
    @DisplayName("유저잠금해제 이벤트가 오면 redis에서 유저를 잠금해제처리한다")
    void success_shouldUnlockUser_whenUserLockedEventIsFalse() {
      // given
      UUID userId = UUID.randomUUID();
      UserLockUpdatedEvent event = new UserLockUpdatedEvent(userId, false);

      // when
      eventListener.onUserLockUpdatedEvent(event);

      // then
      then(jwtRegistry).should(never()).lockUser(any(UUID.class));
      then(jwtRegistry).should(times(1)).unlockUser(userId);
    }
  }

  @Nested
  class UserLockUpdatedRecover {
    @Test
    @DisplayName("Recover함수가 호출되면 예외를 정상적으로 처리한다")
    void success_shouldNotThrowException_whenRecoverMethodIsCalled() {
      // given
      DataAccessException e = new RedisConnectionFailureException("test");
      UserLockUpdatedEvent event = new UserLockUpdatedEvent(UUID.randomUUID(), true);

      // when & then
      assertDoesNotThrow(() -> eventListener.userLockUpdatedRecover(e, event));
    }
  }
}
