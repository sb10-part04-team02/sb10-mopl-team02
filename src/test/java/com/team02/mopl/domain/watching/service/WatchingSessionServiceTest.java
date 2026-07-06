package com.team02.mopl.domain.watching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
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
import com.team02.mopl.domain.watching.mapper.WatchingSessionMapper;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchingSessionServiceTest {

  @Mock private WatchingSessionRepository watchingSessionRepository;
  @Mock private ContentRepository contentRepository;
  @Mock private TagRepository tagRepository;
  @Mock private UserRepository userRepository;
  @Mock private WatchingSessionMapper watchingSessionMapper;

  @InjectMocks private WatchingSessionService watchingSessionService;

  @Test
  @DisplayName("콘텐츠가 없으면 ContentNotFoundException이 발생하고 세션을 조회하지 않는다")
  void getWatchingSessionsByContent_contentNotFound_throwsException() {
    // given
    UUID contentId = UUID.randomUUID();
    given(contentRepository.findByIdAndDeletedAtIsNull(contentId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(
            () ->
                watchingSessionService.getWatchingSessionsByContent(
                    contentId, request(null, null, null)))
        .isInstanceOf(ContentNotFoundException.class);
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  @DisplayName("limit보다 많이 조회되면 hasNext=true와 다음 커서를 반환한다")
  void getWatchingSessionsByContent_hasNext_whenResultsExceedLimit() {
    // given
    UUID contentId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));

    User watcher = mockUser(UUID.randomUUID(), "시청자", null);
    UUID sessionId1 = UUID.randomUUID();
    Instant t1 = Instant.parse("2026-06-29T02:00:00Z");
    WatchingSession session1 = mockSession(sessionId1, t1, watcher);
    WatchingSession session2 =
        mockSession(UUID.randomUUID(), Instant.parse("2026-06-29T01:00:00Z"), watcher);
    given(
            watchingSessionRepository.findActiveSessionsByCursor(
                eq(contentId), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(session1, session2)); // 2건
    given(watchingSessionRepository.countActiveSessions(contentId, null)).willReturn(2L);

    // when
    CursorResponse<WatchingSessionDto> result =
        watchingSessionService.getWatchingSessionsByContent(contentId, request(null, 1, null));

    // then
    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isEqualTo(t1.toString());
    assertThat(result.nextIdAfter()).isEqualTo(sessionId1);
  }

  @Test
  @DisplayName("limit 이하로 조회되면 hasNext=false이고 다음 커서는 null이다")
  void getWatchingSessionsByContent_noNext_whenResultsWithinLimit() {
    // given
    UUID contentId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));

    User watcher = mockUser(UUID.randomUUID(), "시청자", null);
    WatchingSession session =
        mockSession(UUID.randomUUID(), Instant.parse("2026-06-29T01:00:00Z"), watcher);
    given(
            watchingSessionRepository.findActiveSessionsByCursor(
                eq(contentId), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of(session)); // 1건
    given(watchingSessionRepository.countActiveSessions(contentId, null)).willReturn(1L);

    // when
    CursorResponse<WatchingSessionDto> result =
        watchingSessionService.getWatchingSessionsByContent(contentId, request(null, 10, null));

    // then
    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isFalse();
    assertThat(result.nextCursor()).isNull();
    assertThat(result.nextIdAfter()).isNull();
  }

  @Test
  @DisplayName("limit과 sortDirection이 없으면 기본값(20, DESCENDING)으로 정규화한다")
  void getWatchingSessionsByContent_normalizesLimitAndDirection() {
    // given
    UUID contentId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));
    given(
            watchingSessionRepository.findActiveSessionsByCursor(
                any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());
    given(watchingSessionRepository.countActiveSessions(any(), any())).willReturn(0L);

    // when
    CursorResponse<WatchingSessionDto> result =
        watchingSessionService.getWatchingSessionsByContent(contentId, request(null, null, null));

    // then
    verify(watchingSessionRepository)
        .findActiveSessionsByCursor(
            eq(contentId), isNull(), eq(SortDirection.DESCENDING), isNull(), isNull(), eq(21));
    assertThat(result.sortBy()).isEqualTo(WatchingSessionSortBy.createdAt.name());
    assertThat(result.sortDirection()).isEqualTo(SortDirection.DESCENDING.name());
  }

  @Test
  @DisplayName("watcherNameLike는 공백이면 null로, 값이 있으면 trim해서 전달한다")
  void getWatchingSessionsByContent_normalizesWatcherNameLike() {
    // given
    UUID contentId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));
    given(
            watchingSessionRepository.findActiveSessionsByCursor(
                any(), any(), any(), any(), any(), anyInt()))
        .willReturn(List.of());
    given(watchingSessionRepository.countActiveSessions(any(), any())).willReturn(0L);

    // when: watcherNameLike가 공백뿐이면
    watchingSessionService.getWatchingSessionsByContent(contentId, request("   ", null, null));
    // then: null로 정규화되어 전달된다
    verify(watchingSessionRepository)
        .findActiveSessionsByCursor(eq(contentId), isNull(), any(), any(), any(), anyInt());

    // when: watcherNameLike 앞뒤에 공백이 있으면
    watchingSessionService.getWatchingSessionsByContent(contentId, request(" Alice ", null, null));
    // then: trim되어 전달된다
    verify(watchingSessionRepository)
        .findActiveSessionsByCursor(eq(contentId), eq("Alice"), any(), any(), any(), anyInt());
    verify(watchingSessionRepository).countActiveSessions(contentId, "Alice");
  }

  @Test
  @DisplayName("totalCount는 필터를 반영한 카운트 결과를 사용한다")
  void getWatchingSessionsByContent_totalCountFromFilteredCount() {
    // given
    UUID contentId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));
    given(
            watchingSessionRepository.findActiveSessionsByCursor(
                any(), anyString(), any(), any(), any(), anyInt()))
        .willReturn(List.of());
    given(watchingSessionRepository.countActiveSessions(contentId, "Alice")).willReturn(7L);

    // when
    CursorResponse<WatchingSessionDto> result =
        watchingSessionService.getWatchingSessionsByContent(
            contentId, request("Alice", null, SortDirection.ASCENDING));

    // then
    assertThat(result.totalCount()).isEqualTo(7L);
    assertThat(result.sortDirection()).isEqualTo(SortDirection.ASCENDING.name());
  }

  @Test
  @DisplayName("cursor만 있고 idAfter가 없으면 INVALID_REQUEST 예외가 발생한다")
  void getWatchingSessionsByContent_cursorWithoutIdAfter_throwsInvalidRequest() {
    // given
    UUID contentId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));
    WatchingSessionSearchRequest request =
        new WatchingSessionSearchRequest(null, "2026-06-29T00:00:00Z", null, null, null, null);

    // when & then
    assertThatThrownBy(
            () -> watchingSessionService.getWatchingSessionsByContent(contentId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  @DisplayName("잘못된 cursor 형식이면 INVALID_REQUEST 예외가 발생한다")
  void getWatchingSessionsByContent_invalidCursorFormat_throwsInvalidRequest() {
    // given
    UUID contentId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));
    WatchingSessionSearchRequest request =
        new WatchingSessionSearchRequest(null, "not-a-date", UUID.randomUUID(), null, null, null);

    // when & then
    assertThatThrownBy(
            () -> watchingSessionService.getWatchingSessionsByContent(contentId, request))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  @DisplayName("join은 시청 세션을 저장하고 JOIN 변경 정보와 시청자 수를 반환한다")
  void join_savesSessionAndReturnsJoinChange() {
    // given
    UUID contentId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Content content = mockContent(contentId);
    givenContent(contentId, content);
    User watcher = mockUser(userId, "시청자", null);
    given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.of(watcher));

    WatchingSession saved = mockSession(UUID.randomUUID(), Instant.now(), watcher);
    given(watchingSessionRepository.save(any(WatchingSession.class))).willReturn(saved);
    given(watchingSessionRepository.countActiveByContentId(contentId)).willReturn(3L);

    WatchingSessionDto dto =
        new WatchingSessionDto(saved.getId(), saved.getCreatedAt(), null, null);
    given(watchingSessionMapper.toDto(eq(saved), any())).willReturn(dto);

    // when
    WatchingSessionChange change = watchingSessionService.join(contentId, userId);

    // then
    assertThat(change.type()).isEqualTo(ChangeType.JOIN);
    assertThat(change.watchingSession()).isEqualTo(dto);
    assertThat(change.watcherCount()).isEqualTo(3L);
    verify(watchingSessionRepository).save(any(WatchingSession.class));
  }

  @Test
  @DisplayName("join 시 콘텐츠가 없으면 ContentNotFoundException이 발생한다")
  void join_contentNotFound_throwsException() {
    // given
    UUID contentId = UUID.randomUUID();
    given(contentRepository.findByIdAndDeletedAtIsNull(contentId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> watchingSessionService.join(contentId, UUID.randomUUID()))
        .isInstanceOf(ContentNotFoundException.class);
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  @DisplayName("join 시 사용자가 없으면 UserNotFoundException이 발생한다")
  void join_userNotFound_throwsException() {
    // given
    UUID contentId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    givenContent(contentId, mockContent(contentId));
    given(userRepository.findByIdAndDeletedAtIsNull(userId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> watchingSessionService.join(contentId, userId))
        .isInstanceOf(UserNotFoundException.class);
    verifyNoInteractions(watchingSessionRepository);
  }

  @Test
  @DisplayName("leave는 활성 세션을 종료하고 LEAVE 변경 정보를 반환한다")
  void leave_exitsActiveSessionAndReturnsLeaveChange() {
    // given
    UUID contentId = UUID.randomUUID();
    Content content = mockContent(contentId);
    given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(List.of());

    UUID watchingSessionId = UUID.randomUUID();
    WatchingSession session =
        mockSession(watchingSessionId, Instant.now(), mockUser(UUID.randomUUID(), "시청자", null));
    given(session.getExitedAt()).willReturn(null);
    given(session.getContent()).willReturn(content);
    given(watchingSessionRepository.findById(watchingSessionId)).willReturn(Optional.of(session));
    given(watchingSessionRepository.countActiveByContentId(contentId)).willReturn(0L);

    WatchingSessionDto dto = new WatchingSessionDto(watchingSessionId, Instant.now(), null, null);
    given(watchingSessionMapper.toDto(eq(session), any())).willReturn(dto);

    // when
    Optional<WatchingSessionChange> change = watchingSessionService.leave(watchingSessionId);

    // then
    assertThat(change).isPresent();
    assertThat(change.get().type()).isEqualTo(ChangeType.LEAVE);
    assertThat(change.get().watchingSession()).isEqualTo(dto);
    assertThat(change.get().watcherCount()).isEqualTo(0L);
    verify(session).exit();
  }

  @Test
  @DisplayName("leave 시 이미 종료된 세션이면 empty를 반환하고 종료하지 않는다")
  void leave_alreadyExited_returnsEmpty() {
    // given
    UUID watchingSessionId = UUID.randomUUID();
    WatchingSession session =
        mockSession(watchingSessionId, Instant.now(), mockUser(UUID.randomUUID(), "시청자", null));
    given(session.getExitedAt()).willReturn(Instant.now());
    given(watchingSessionRepository.findById(watchingSessionId)).willReturn(Optional.of(session));

    // when
    Optional<WatchingSessionChange> change = watchingSessionService.leave(watchingSessionId);

    // then
    assertThat(change).isEmpty();
    verify(session, never()).exit();
  }

  @Test
  @DisplayName("leave 시 세션이 없으면 empty를 반환한다")
  void leave_sessionNotFound_returnsEmpty() {
    // given
    UUID watchingSessionId = UUID.randomUUID();
    given(watchingSessionRepository.findById(watchingSessionId)).willReturn(Optional.empty());

    // when & then
    assertThat(watchingSessionService.leave(watchingSessionId)).isEmpty();
  }

  private WatchingSessionSearchRequest request(
      String watcherNameLike, Integer limit, SortDirection direction) {
    return new WatchingSessionSearchRequest(watcherNameLike, null, null, limit, direction, null);
  }

  private void givenContent(UUID contentId, Content content) {
    given(contentRepository.findByIdAndDeletedAtIsNull(contentId)).willReturn(Optional.of(content));
    given(tagRepository.findByContentIdAndDeletedAtIsNull(contentId)).willReturn(List.of());
  }

  private Content mockContent(UUID contentId) {
    Content content = mock(Content.class);
    given(content.getId()).willReturn(contentId);
    given(content.getContentType()).willReturn(ContentType.MOVIE);
    given(content.getTitle()).willReturn("테스트 영화");
    given(content.getDescription()).willReturn("설명");
    given(content.getThumbnailUrl()).willReturn("http://img");
    given(content.getAverageRating()).willReturn(4.5);
    given(content.getReviewCount()).willReturn(2);
    return content;
  }

  private User mockUser(UUID userId, String name, String profileImageUrl) {
    User user = mock(User.class);
    given(user.getId()).willReturn(userId);
    given(user.getName()).willReturn(name);
    given(user.getProfileImageUrl()).willReturn(profileImageUrl);
    return user;
  }

  private WatchingSession mockSession(UUID sessionId, Instant createdAt, User user) {
    WatchingSession session = mock(WatchingSession.class);
    given(session.getId()).willReturn(sessionId);
    given(session.getCreatedAt()).willReturn(createdAt);
    given(session.getUser()).willReturn(user);
    return session;
  }
}
