package com.team02.mopl.domain.watching.service;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.domain.watching.dto.WatchingSessionDto;
import com.team02.mopl.domain.watching.dto.WatchingSessionSearchRequest;
import com.team02.mopl.domain.watching.entity.WatchingSession;
import com.team02.mopl.domain.watching.enums.WatchingSessionSortBy;
import com.team02.mopl.domain.watching.mapper.WatchingSessionMapper;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import com.team02.mopl.domain.watching.util.WatchingSessionCursorConverter;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchingSessionService {

  private final WatchingSessionRepository watchingSessionRepository;
  private final ContentRepository contentRepository;
  private final TagRepository tagRepository;
  private final WatchingSessionMapper watchingSessionMapper;

  // 특정 콘텐츠의 활성 시청 세션 목록 조회 (커서 페이지네이션 + 시청자 이름 필터)
  @Transactional(readOnly = true)
  public CursorResponse<WatchingSessionDto> getWatchingSessionsByContent(
      UUID contentId, WatchingSessionSearchRequest request) {
    Content content =
        contentRepository
            .findByIdAndDeletedAtIsNull(contentId)
            .orElseThrow(ContentNotFoundException::new);

    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction = CursorPageRequest.normalizeSortDirection(request.sortDirection());
    String watcherName =
        (request.watcherNameLike() != null && !request.watcherNameLike().isBlank())
            ? request.watcherNameLike().trim()
            : null;

    // 커서 문자열 형식 검증 및 정렬 키(Instant) 변환
    Instant cursor = WatchingSessionCursorConverter.toSortKey(request.cursor());
    if ((cursor == null) != (request.idAfter() == null)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    // hasNext 판정
    List<WatchingSession> rows =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, watcherName, direction, cursor, request.idAfter(), limit + 1);
    boolean hasNext = rows.size() > limit;
    List<WatchingSession> page = hasNext ? rows.subList(0, limit) : rows;

    // ContentSummary는 한 번만 만들어서 재사용
    List<Tag> tags = tagRepository.findByContentIdAndDeletedAtIsNull(content.getId());
    ContentSummary contentSummary = watchingSessionMapper.toContentSummary(content, tags);
    List<WatchingSessionDto> data =
        page.stream().map(session -> watchingSessionMapper.toDto(session, contentSummary)).toList();

    long totalCount = watchingSessionRepository.countActiveSessions(contentId, watcherName);

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      WatchingSession last = page.get(page.size() - 1);
      nextCursor = last.getCreatedAt().toString();
      nextIdAfter = last.getId();
    }

    return new CursorResponse<>(
        data,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        WatchingSessionSortBy.CREATED_AT.name(),
        direction.name());
  }
}
