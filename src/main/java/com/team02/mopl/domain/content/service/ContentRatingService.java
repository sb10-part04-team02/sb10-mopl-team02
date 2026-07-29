package com.team02.mopl.domain.content.service;

import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.content.repository.ContentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentRatingService {

  private final ContentRepository contentRepository;

  // 리뷰 변경(생성·수정·삭제) 시 콘텐츠의 평균 평점·리뷰 수를 재집계해 갱신
  // 활성 리뷰를 전부 다시 집계하므로 정합성이 보장됨 (단일 UPDATE로 처리)
  @Transactional
  public void refreshAggregate(UUID contentId) {
    int updated = contentRepository.refreshRatingAggregate(contentId);
    if (updated == 0) {
      throw new ContentNotFoundException();
    }
    log.debug("content.rating_refreshed contentId={}", contentId);
  }
}
