package com.team02.mopl.domain.watching.service;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.domain.watching.dto.WatchingSessionChange;
import com.team02.mopl.domain.watching.dto.WatchingSessionDto;
import com.team02.mopl.domain.watching.dto.WatchingSessionSearchRequest;
import com.team02.mopl.domain.watching.entity.WatchingSession;
import com.team02.mopl.domain.watching.enums.ChangeType;
import com.team02.mopl.domain.watching.enums.WatchingSessionSortBy;
import com.team02.mopl.domain.watching.event.WatchingSessionJoinedEvent;
import com.team02.mopl.domain.watching.exception.WatchingSessionForbiddenException;
import com.team02.mopl.domain.watching.mapper.WatchingSessionMapper;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import com.team02.mopl.domain.watching.util.WatchingSessionCursorConverter;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.InvalidCursorRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchingSessionService {

  private final WatchingSessionRepository watchingSessionRepository;
  private final ContentRepository contentRepository;
  private final TagRepository tagRepository;
  private final UserRepository userRepository;
  private final WatchingSessionMapper watchingSessionMapper;
  private final ApplicationEventPublisher eventPublisher;

  // 시청 세션 참여: 세션을 생성하고 구독자들에게 전파할 JOIN 변경 정보를 반환한다.
  @Transactional
  public WatchingSessionChange join(UUID contentId, UUID userId) {
    Content content =
        contentRepository
            .findByIdAndDeletedAtIsNull(contentId)
            .orElseThrow(ContentNotFoundException::new);
    User watcher =
        userRepository.findByIdAndDeletedAtIsNull(userId).orElseThrow(UserNotFoundException::new);

    // 유저·콘텐츠당 삭제되지 않은 세션은 1건만 허용(부분 유니크 인덱스)되므로,
    // 중복 SUBSCRIBE(중복 탭, 재연결)로 활성 세션이 이미 있으면 새로 만들지 않고 재사용한다.
    // leave가 종료와 소프트 삭제를 함께 수행하므로 삭제되지 않은 세션은 항상 활성 상태다.
    Optional<WatchingSession> existingSession =
        watchingSessionRepository.findByContent_IdAndUser_IdAndDeletedAtIsNull(contentId, userId);

    WatchingSession session;
    if (existingSession.isPresent()) {
      session = existingSession.get();
      log.debug(
          "watching.session_join_reused contentId={} userId={} sessionId={}",
          contentId,
          userId,
          session.getId());
    } else {
      session =
          watchingSessionRepository.save(
              new WatchingSession(content, watcher, Instant.now(), null));

      // 새 시청 세션이 생성된 경우에만 팔로워 알림 이벤트를 발행한다.
      // 기존 활성 세션 재사용 시에도 이벤트를 발행하면 새로고침/재연결마다 중복 알림이 생성될 수 있다.
      eventPublisher.publishEvent(
          new WatchingSessionJoinedEvent(
              watcher.getId(), watcher.getName(), content.getId(), content.getTitle()));
      log.info(
          "watching.session_joined contentId={} userId={} sessionId={}",
          contentId,
          userId,
          session.getId());
    }

    return toChange(ChangeType.JOIN, session, content);
  }

  // 시청 세션 이탈: 세션을 종료하고 전파할 LEAVE 변경 정보를 반환한다. 이미 종료됐거나 없으면 empty.
  @Transactional
  public Optional<WatchingSessionChange> leave(UUID watchingSessionId, UUID requesterId) {
    return watchingSessionRepository
        .findById(watchingSessionId)
        .filter(session -> session.getExitedAt() == null && !session.isDeleted())
        .map(
            session -> {
              // 세션 소유자만 종료할 수 있다.
              if (!session.getUser().getId().equals(requesterId)) {
                throw new WatchingSessionForbiddenException();
              }
              session.exit();
              // 부분 유니크 인덱스(deleted_at IS NULL) 자리를 비워 재시청 시 새 세션을 만들 수 있게 한다.
              session.delete();
              log.info(
                  "watching.session_left sessionId={} userId={}", watchingSessionId, requesterId);
              return toChange(ChangeType.LEAVE, session, session.getContent());
            });
  }

  private WatchingSessionChange toChange(
      ChangeType type, WatchingSession session, Content content) {
    List<Tag> tags = tagRepository.findByContentIdAndDeletedAtIsNull(content.getId());
    WatchingSessionDto dto =
        watchingSessionMapper.toDto(session, watchingSessionMapper.toContentSummary(content, tags));
    // JPQL 실행 전 auto-flush로 방금 생성/종료된 세션이 집계에 반영된다.
    long watcherCount = watchingSessionRepository.countActiveByContentId(content.getId());
    return new WatchingSessionChange(type, dto, watcherCount);
  }

  // 특정 사용자가 현재 시청 중인 세션 1건 조회. 없으면 null(활성 세션 없음).
  @Transactional(readOnly = true)
  public WatchingSessionDto getWatchingSessionByWatcher(UUID watcherId) {
    userRepository.findByIdAndDeletedAtIsNull(watcherId).orElseThrow(UserNotFoundException::new);

    return watchingSessionRepository
        .findFirstByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(watcherId)
        .map(
            session -> {
              Content content = session.getContent();
              List<Tag> tags = tagRepository.findByContentIdAndDeletedAtIsNull(content.getId());
              return watchingSessionMapper.toDto(
                  session, watchingSessionMapper.toContentSummary(content, tags));
            })
        .orElse(null);
  }

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

    // 커서와 idAfter는 함께 제공되거나 모두 생략되어야 한다.
    if (!CursorPageRequest.isValidCursorCombo(request.cursor(), request.idAfter())) {
      throw new InvalidCursorRequestException();
    }
    // 커서 문자열 형식 검증 및 정렬 키(Instant) 변환
    Instant cursor = WatchingSessionCursorConverter.toSortKey(request.cursor());

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
        WatchingSessionSortBy.createdAt.name(),
        direction.name());
  }
}
