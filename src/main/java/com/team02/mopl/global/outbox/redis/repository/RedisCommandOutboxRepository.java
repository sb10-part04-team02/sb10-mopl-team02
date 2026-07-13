package com.team02.mopl.global.outbox.redis.repository;

import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

public interface RedisCommandOutboxRepository extends JpaRepository<RedisCommandOutbox, UUID> {

  List<RedisCommandOutbox> findAllByDeletedAtIsNullOrderByCreatedAtAsc();

  @Modifying
  int deleteTop1000ByDeletedAtIsNotNull();
}
