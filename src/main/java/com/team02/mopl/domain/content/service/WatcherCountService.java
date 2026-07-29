package com.team02.mopl.domain.content.service;

import com.team02.mopl.domain.watching.repository.WatcherCountProjection;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WatcherCountService {

  private final WatchingSessionRepository watchingSessionRepository;

  // 단일 contentId에 대해 watcherCount(활성 시청 세션 수) 집계
  @Transactional(readOnly = true)
  public long count(UUID contentId) {
    return watchingSessionRepository.countActiveByContentId(contentId);
  }

  // 콘텐츠 목록을 조회할 때 각 콘텐츠의 시청자 수도 함께 보여줘야 함
  // contentId 20개를 한 번에 넘겨서 쿼리 1번으로 해결
  // 여러 contentId의 watcherCount 일괄 집계 (목록 조회 N+1 방지)
  @Transactional(readOnly = true)
  public Map<UUID, Long> countByContentIds(List<UUID> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return Map.of(); // 빈 IN 절 방지
    }
    return watchingSessionRepository.countActiveByContentIds(contentIds).stream()
        .collect(
            Collectors.toMap(
                WatcherCountProjection::getContentId,
                WatcherCountProjection::getCount)); // Map<UUID, Long> 으로 반환 (contentId, 시청자 수)
  }
}
