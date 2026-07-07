package com.team02.mopl.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
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
}
