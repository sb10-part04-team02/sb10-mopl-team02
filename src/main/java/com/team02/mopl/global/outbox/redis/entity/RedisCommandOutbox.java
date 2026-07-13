package com.team02.mopl.global.outbox.redis.entity;

import com.team02.mopl.global.entity.BaseEntity;
import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;
import com.team02.mopl.global.outbox.redis.entity.enums.OutboxTarget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "redis_outboxes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RedisCommandOutbox extends BaseEntity {
  @Column(nullable = false)
  private UUID targetId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OutboxTarget target;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private CommandType commandType;

  public RedisCommandOutbox(CommandType commandType, UUID targetId, OutboxTarget target) {
    this.commandType = Objects.requireNonNull(commandType, "commandType은 null일 수 없습니다.");
    this.targetId = Objects.requireNonNull(targetId, "targetId는 null일 수 없습니다.");
    this.target = Objects.requireNonNull(target, "target은 null일 수 없습니다.");
  }
}
