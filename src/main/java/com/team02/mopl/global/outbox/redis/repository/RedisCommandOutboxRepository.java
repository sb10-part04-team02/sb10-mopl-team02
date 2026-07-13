package com.team02.mopl.global.outbox.redis.repository;

import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RedisCommandOutboxRepository extends JpaRepository<RedisCommandOutbox, UUID> {

  List<RedisCommandOutbox>
      findTop1000ByProcessedFalseAndDeletedAtIsNullAndRetryCountLessThanEqualOrderByCreatedAtAsc(
          int count);

  @Modifying
  @Query(
      value =
          """
  DELETE FROM redis_outboxes
   WHERE id IN (SELECT id FROM redis_outboxes WHERE processed = true ORDER BY id LIMIT 1000)
""",
      nativeQuery = true)
  int deleteTop1000ByProcessedTrue();
}
