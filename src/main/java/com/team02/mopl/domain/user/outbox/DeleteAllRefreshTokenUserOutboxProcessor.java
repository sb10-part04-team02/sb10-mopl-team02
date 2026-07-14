package com.team02.mopl.domain.user.outbox;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.entity.RedisOutBoxProcessor;
import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;
import com.team02.mopl.global.outbox.redis.entity.enums.OutboxTarget;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteAllRefreshTokenUserOutboxProcessor implements RedisOutBoxProcessor {

  private final JwtRegistry jwtRegistry;

  @Override
  public boolean supports(CommandType commandType) {
    return CommandType.DELETE_ALL_REFRESH_TOKEN.equals(commandType);
  }

  @Override
  public void process(RedisCommandOutbox outbox) {
    UUID userId = OutboxTarget.USER.equals(outbox.getTarget()) ? outbox.getTargetId() : null;
    if (userId == null) {
      throw new IllegalStateException("Target 불일치");
    }

    jwtRegistry.deleteAllRefreshToken(userId);
  }
}
