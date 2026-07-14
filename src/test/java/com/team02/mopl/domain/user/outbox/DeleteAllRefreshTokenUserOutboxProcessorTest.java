package com.team02.mopl.domain.user.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;
import com.team02.mopl.global.outbox.redis.entity.enums.OutboxTarget;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteAllRefreshTokenUserOutboxProcessorTest {

  @Mock private JwtRegistry jwtRegistry;
  @InjectMocks private DeleteAllRefreshTokenUserOutboxProcessor outboxProcessor;

  @Nested
  class Supports {
    @Test
    @DisplayName("지원하지 않는 커맨드타입이면 false를 반환한다")
    void fail_shouldReturnFalse_whenCommandTypeIsUnsupported() {
      // given
      CommandType unsupportedType = mock(CommandType.class);

      // when
      boolean result = outboxProcessor.supports(unsupportedType);

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("DELETE_ALL_REFRESH_TOKEN 타입이면 true를 반환한다")
    void success_shouldReturnTrue_whenTypeMatches() {
      // given
      CommandType supportedType = CommandType.DELETE_ALL_REFRESH_TOKEN;

      // when
      boolean result = outboxProcessor.supports(supportedType);

      // then
      assertThat(result).isTrue();
    }
  }

  @Nested
  class Process {
    @Test
    @DisplayName("아웃박스 타겟이 USER가 아니면 예외를 던진다")
    void fail_shouldThrowException_whenOutboxTargetIsNotUser() {
      // given
      OutboxTarget unsupportedTarget = mock(OutboxTarget.class);
      RedisCommandOutbox outbox =
          new RedisCommandOutbox(
              CommandType.DELETE_ALL_REFRESH_TOKEN, UUID.randomUUID(), unsupportedTarget);

      // when & then
      assertThrows(IllegalStateException.class, () -> outboxProcessor.process(outbox));
      then(jwtRegistry).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("아웃박스 타겟이 USER면 모든 리프레시토큰을 삭제한다")
    void success_shouldDeleteAllRefreshTokens_whenOutboxTargetIsUser() {
      // given
      UUID userId = UUID.randomUUID();
      OutboxTarget supportedTarget = OutboxTarget.USER;
      RedisCommandOutbox outbox =
          new RedisCommandOutbox(CommandType.DELETE_ALL_REFRESH_TOKEN, userId, supportedTarget);

      // when & then
      assertDoesNotThrow(() -> outboxProcessor.process(outbox));
      then(jwtRegistry).should(times(1)).deleteAllRefreshToken(eq(userId));
    }
  }
}
