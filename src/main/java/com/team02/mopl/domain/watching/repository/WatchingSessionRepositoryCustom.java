package com.team02.mopl.domain.watching.repository;

import com.team02.mopl.domain.watching.entity.WatchingSession;
import com.team02.mopl.global.enums.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WatchingSessionRepositoryCustom {

  // 특정 콘텐츠의 활성 시청 세션 목록 (복합 커서 페이지네이션 + 시청자 이름 필터)
  List<WatchingSession> findActiveSessionsByCursor(
      UUID contentId,
      String watcherNameLike,
      SortDirection direction,
      Instant cursor,
      UUID idAfter,
      int limit);

  // totalCount 산출용 - 목록 조회와 동일한 필터(활성 세션 + 이름 필터) 적용
  long countActiveSessions(UUID contentId, String watcherNameLike);
}
