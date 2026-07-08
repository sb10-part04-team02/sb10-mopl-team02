package com.team02.mopl.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import com.team02.mopl.domain.user.event.UserLockUpdatedEvent;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

@ExtendWith(MockitoExtension.class)
class UserEventListenerTest {

  @Mock private JwtRegistry jwtRegistry;
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
      then(jwtRegistry).should(times(1)).deleteAllRefreshToken(userId);
    }

    @Test
    @DisplayName("레디스에 문제가 생기면 예외를 던지지만 catch로 방어한다")
    void fail_shouldDefenceException_whenRedisIsDown() {
      // given
      UUID userId = UUID.randomUUID();
      RoleUpdatedEvent event = new RoleUpdatedEvent(userId, Role.USER, Role.ADMIN);
      willThrow(RedisConnectionFailureException.class)
          .given(jwtRegistry)
          .deleteAllRefreshToken(userId);

      // when & then
      assertDoesNotThrow(() -> eventListener.onUserRoleUpdated(event));
      then(jwtRegistry).should(times(1)).deleteAllRefreshToken(userId);
    }
  }

  @Nested
  class OnUserLocked {

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

    @Test
    @DisplayName("계정 잠금 중 레디스에 문제가 생기면 예외를 던지지만 catch로 방어한다")
    void fail_shouldDefenceException_whenRedisIsDownDuringLock() {
      // given
      UUID userId = UUID.randomUUID();
      UserLockUpdatedEvent event = new UserLockUpdatedEvent(userId, true);
      willThrow(RedisConnectionFailureException.class).given(jwtRegistry).lockUser(userId);

      // when & then
      assertDoesNotThrow(() -> eventListener.onUserLockUpdatedEvent(event));
      then(jwtRegistry).should(times(1)).lockUser(userId);
      then(jwtRegistry).should(never()).unlockUser(any(UUID.class));
    }

    @Test
    @DisplayName("계정 잠금 해제중 레디스에 문제가 생기면 예외를 던지지만 catch로 방어한다")
    void fail_shouldDefenceException_whenRedisIsDownDuringUnlock() {
      // given
      UUID userId = UUID.randomUUID();
      UserLockUpdatedEvent event = new UserLockUpdatedEvent(userId, false);
      willThrow(RedisConnectionFailureException.class).given(jwtRegistry).unlockUser(userId);

      // when & then
      assertDoesNotThrow(() -> eventListener.onUserLockUpdatedEvent(event));
      then(jwtRegistry).should(never()).lockUser(any(UUID.class));
      then(jwtRegistry).should(times(1)).unlockUser(userId);
    }
  }
}
