package com.team02.mopl.domain.watching.repository;

import com.team02.mopl.domain.watching.entity.WatchingSession;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchingSessionRepository extends JpaRepository<WatchingSession, UUID> {

  // 단건 콘텐츠의 활성 시청자 수
  @Query(
      "select count(ws) from WatchingSession ws "
          + "where ws.content.id = :contentId and ws.exitedAt is null and ws.deletedAt is null")
  long countActiveByContentId(@Param("contentId") UUID contentId);
}
