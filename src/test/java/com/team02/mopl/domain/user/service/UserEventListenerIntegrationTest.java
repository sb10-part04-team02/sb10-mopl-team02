package com.team02.mopl.domain.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.user.event.UserLockUpdatedEvent;
import com.team02.mopl.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// AOP와 Retryable의 Recoverer테스트 확인하기위한 통합테스트임
public class UserEventListenerIntegrationTest extends IntegrationTestSupport {

  @MockitoBean private JwtRegistry jwtRegistry;
  @MockitoSpyBean private UserEventListener eventListener;

  @Test
  @DisplayName("계정 잠금시 예외가 3번 발생시 재시도후 Recover가 호출된다")
  void fail_shouldInvokeRecover_whenRedisIsDownDuringLock() {
    // given
    UUID userId = UUID.randomUUID();
    UserLockUpdatedEvent event = new UserLockUpdatedEvent(userId, true);

    willThrow(new RecoverableDataAccessException("redis 연결실패")).given(jwtRegistry).lockUser(userId);

    // when & then
    assertDoesNotThrow(() -> eventListener.onUserLockUpdatedEvent(event));
    then(jwtRegistry).should(times(3)).lockUser(userId);
    then(eventListener)
        .should(times(1))
        .userLockUpdatedRecover(any(DataAccessException.class), eq(event));
  }
}
