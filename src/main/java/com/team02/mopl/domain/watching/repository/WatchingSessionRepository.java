package com.team02.mopl.domain.watching.repository;

import com.team02.mopl.domain.watching.entity.WatchingSession;
import java.util.List;
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

  // 여러 콘텐츠의 활성 시청자 수 일괄 집계 (목록 조회 N+1 방지)
  // 시청자 0건인 콘텐츠는 결과 행에 없음 -> 호출부에서 0 디폴트 처리
  // WatcherCountProjection 에 담아서 서비스 계층에 반환
  @Query(
      "select ws.content.id as contentId, count(ws) as count from WatchingSession ws "
          + "where ws.content.id in :contentIds "
          + "and ws.exitedAt is null and ws.deletedAt is null "
          + "group by ws.content.id")
  List<WatcherCountProjection> countActiveByContentIds(@Param("contentIds") List<UUID> contentIds);
}
