package com.team02.mopl.domain.content.service;

import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatcherCountService {

  private final WatchingSessionRepository watchingSessionRepository;

  // 단일 contentId에 대해 watcherCount(활성 시청 세션 수) 집계
  public long count(UUID contentId) {
    return watchingSessionRepository.countActiveByContentId(contentId);
  }
}
