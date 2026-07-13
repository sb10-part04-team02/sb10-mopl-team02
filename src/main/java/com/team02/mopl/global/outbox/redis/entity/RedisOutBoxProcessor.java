package com.team02.mopl.global.outbox.redis.entity;

import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;

public interface RedisOutBoxProcessor {
  boolean supports(CommandType commandType);

  void process(RedisCommandOutbox outbox);
}
