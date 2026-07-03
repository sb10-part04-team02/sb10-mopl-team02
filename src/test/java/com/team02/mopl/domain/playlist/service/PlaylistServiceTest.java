package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.domain.follow.entity.Follow;
import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.dto.PlaylistSearchRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistUpdateRequest;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.entity.PlaylistContent;
import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.domain.playlist.exception.PlaylistForbiddenException;
import com.team02.mopl.domain.playlist.exception.PlaylistNotFoundException;
import com.team02.mopl.domain.playlist.mapper.PlaylistMapper;
import com.team02.mopl.domain.playlist.repository.PlaylistContentRepository;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.subscription.entity.Subscription;
import com.team02.mopl.domain.subscription.repository.SubscriptionRepository;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

// TODO: JWT 인증 연결 후 PlaylistController @WebMvcTest 추가
//       (201 응답 / @Valid 검증 실패 400 / 인증된 요청자 UUID 바인딩 검증).
@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

  @Mock PlaylistRepository playlistRepository;

  @Mock PlaylistContentRepository playlistContentRepository;

  @Mock ContentRepository contentRepository;

  @Mock TagRepository tagRepository;

  @Mock UserRepository userRepository;

  @Mock SubscriptionRepository subscriptionRepository;

  @Mock PlaylistMapper playlistMapper;

  @Mock FollowRepository followRepository;

  @Mock NotificationService notificationService;

  @Mock UserRepository userRepository;

  @InjectMocks PlaylistService playlistService;

  @Captor ArgumentCaptor<Playlist> playlistCaptor;

  @Nested
  class Create {
    private final UUID ownerId = UUID.randomUUID();
    private final String ownerName = "우디";
    private final String title = "내 플리";
    private final String description = "설명";

    @Test
    @DisplayName("정상 요청이면 소유자를 요청자로 지정해 저장하고 PlaylistDto를 반환한다")
    void success_whenRequestIsValid() {
      User owner = mockUserWithId(ownerId);
      PlaylistCreateRequest request = new PlaylistCreateRequest(title, description);
      Playlist saved = new Playlist(ownerId, title, description);
      PlaylistDto expect =
          new PlaylistDto(
              UUID.randomUUID(),
              new UserSummary(ownerId, null, null),
              title,
              description,
              Instant.parse("2026-06-29T00:00:00Z"),
              0L,
              false,
              List.of());

      // 플레이리스트 생성 전 owner가 존재하는지 확인
      given(userRepository.findByIdAndDeletedAtIsNull(ownerId)).willReturn(Optional.of(owner));
      given(playlistRepository.save(any(Playlist.class))).willReturn(saved);
      // 팔로워가 없으면 주요 활동 알림을 생성 X
      given(followRepository.findByFollowee_IdAndDeletedAtIsNull(ownerId)).willReturn(List.of());
      given(playlistMapper.toDto(any(Playlist.class), eq(false))).willReturn(expect);

      // when
      PlaylistDto actual = playlistService.create(ownerId, request);

      // then
      assertThat(actual).isEqualTo(expect);
      then(playlistRepository).should().save(playlistCaptor.capture());
      then(playlistMapper).should().toDto(any(Playlist.class), eq(false));
      then(notificationService).shouldHaveNoInteractions();

      Playlist captured = playlistCaptor.getValue();
      assertThat(captured.getOwnerId()).isEqualTo(ownerId);
      assertThat(captured.getTitle()).isEqualTo(title);
      assertThat(captured.getDescription()).isEqualTo(description);
    }

    @Test
    @DisplayName("플레이리스트를 생성하면 생성자를 팔로우 중인 사용자들에게 주요 활동 알림을 생성한다")
    void success_sendsFollowingUserActivityNotifications() {
      UUID followerId = UUID.randomUUID();
      User owner = mockUserWithIdAndName(ownerId, ownerName);
      User follower = mockUserWithId(followerId);
      Follow follow = new Follow(follower, owner);

      PlaylistCreateRequest request = new PlaylistCreateRequest(title, description);
      Playlist saved = new Playlist(ownerId, title, description);
      PlaylistDto expect =
          new PlaylistDto(
              UUID.randomUUID(),
              new UserSummary(ownerId, null, null),
              title,
              description,
              Instant.parse("2026-06-29T00:00:00Z"),
              0L,
              false,
              List.of());

      given(userRepository.findByIdAndDeletedAtIsNull(ownerId)).willReturn(Optional.of(owner));
      given(playlistRepository.save(any(Playlist.class))).willReturn(saved);
      // 플레이리스트 생성자를 팔로우 중인 사용자 목록을 조회
      given(followRepository.findByFollowee_IdAndDeletedAtIsNull(ownerId))
          .willReturn(List.of(follow));
      given(playlistMapper.toDto(any(Playlist.class), eq(false))).willReturn(expect);

      playlistService.create(ownerId, request);

      ArgumentCaptor<NotificationCreateCommand> commandCaptor =
          ArgumentCaptor.forClass(NotificationCreateCommand.class);

      then(notificationService).should().createNotification(commandCaptor.capture());

      NotificationCreateCommand command = commandCaptor.getValue();

      assertThat(command.receiverId()).isEqualTo(followerId);
      assertThat(command.title()).isEqualTo(ownerName + "님이 플레이리스트를 만들었어요.");
      assertThat(command.content()).isEqualTo("[" + title + "] " + description);
      assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
      assertThat(command.notificationType()).isEqualTo(NotificationType.FOLLOWING_USER_ACTIVITY);
    }

    private User mockUserWithId(UUID id) {
      User user = mock(User.class);
      given(user.getId()).willReturn(id);
      return user;
    }

    private User mockUserWithIdAndName(UUID id, String name) {
      User user = mockUserWithId(id);
      given(user.getName()).willReturn(name);
      return user;
    }
  }

  @Nested
  class Update {
    private final UUID playlistId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Test
    @DisplayName("소유자가 요청하면 제목·설명을 수정하고 PlaylistDto를 반환한다")
    void success_whenRequesterIsOwner() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");
      PlaylistDto expect =
          new PlaylistDto(
              playlistId,
              new UserSummary(ownerId, null, null),
              "새 제목",
              "새 설명",
              Instant.parse("2026-06-29T00:00:00Z"),
              0L,
              false,
              List.of());
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));
      given(playlistMapper.toDto(playlist, false)).willReturn(expect);

      // when
      PlaylistDto actual = playlistService.update(playlistId, ownerId, request);

      // then
      assertThat(actual).isEqualTo(expect);
      assertThat(playlist.getTitle()).isEqualTo("새 제목");
      assertThat(playlist.getDescription()).isEqualTo("새 설명");
      then(playlistMapper).should().toDto(playlist, false);
    }

    @Test
    @DisplayName("응답 DTO에 갱신된 updatedAt이 담기도록 toDto 매핑 전에 flush를 호출한다")
    void flushBeforeToDto_whenUpdate() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));
      given(playlistMapper.toDto(playlist, false)).willReturn(null);

      // when
      playlistService.update(playlistId, ownerId, request);

      // then
      // flush로 @PreUpdate(updatedAt 갱신)를 유발한 뒤 매핑해야 응답에 최신 값이 담긴다
      InOrder inOrder = inOrder(playlistRepository, playlistMapper);
      inOrder.verify(playlistRepository).flush();
      inOrder.verify(playlistMapper).toDto(playlist, false);
    }

    @Test
    @DisplayName("title만 전달하면 description은 기존 값을 유지한다")
    void success_whenPartialUpdate() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", null);
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));
      given(playlistMapper.toDto(playlist, false)).willReturn(null);

      // when
      playlistService.update(playlistId, ownerId, request);

      // then
      assertThat(playlist.getTitle()).isEqualTo("새 제목");
      assertThat(playlist.getDescription()).isEqualTo("기존 설명");
    }

    @Test
    @DisplayName("빈 문자열이 전달되면 기존 값을 덮어쓰지 않는다")
    void success_whenBlankIsIgnored() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("", "   ");
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));
      given(playlistMapper.toDto(playlist, false)).willReturn(null);

      // when
      playlistService.update(playlistId, ownerId, request);

      // then
      assertThat(playlist.getTitle()).isEqualTo("기존 제목");
      assertThat(playlist.getDescription()).isEqualTo("기존 설명");
    }

    @Test
    @DisplayName("플레이리스트가 없으면 PLAYLIST_NOT_FOUND 예외를 던진다")
    void fail_whenPlaylistNotFound() {
      // given
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> playlistService.update(playlistId, ownerId, request))
          .isInstanceOf(PlaylistNotFoundException.class);
      then(playlistMapper).should(never()).toDto(any(Playlist.class), eq(false));
    }

    @Test
    @DisplayName("요청자가 소유자가 아니면 FORBIDDEN 예외를 던진다")
    void fail_whenRequesterIsNotOwner() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      UUID otherUserId = UUID.randomUUID();
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));

      // when & then
      assertThatThrownBy(() -> playlistService.update(playlistId, otherUserId, request))
          .isInstanceOf(PlaylistForbiddenException.class);
      assertThat(playlist.getTitle()).isEqualTo("기존 제목");
      then(playlistMapper).should(never()).toDto(any(Playlist.class), eq(false));
    }
  }

  @Nested
  class Get {
    private final UUID playlistId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();
    private final UUID contentId = UUID.randomUUID();

    @Test
    @DisplayName("소유자·포함 콘텐츠·구독 여부를 조립해 PlaylistDto를 반환한다")
    void success_assemblesOwnerContentsAndSubscription() {
      // given
      Playlist playlist = new Playlist(ownerId, "제목", "설명");
      ReflectionTestUtils.setField(playlist, "id", playlistId);
      User owner = new User("홍길동", "hong@test.com", null, null, Role.USER, false);
      Content content = new Content(ContentType.MOVIE, "영화", "설명", "http://img");
      ReflectionTestUtils.setField(content, "id", contentId);
      Tag tag = new Tag(content, "액션");
      UserSummary ownerSummary = new UserSummary(ownerId, "홍길동", null);
      PlaylistDto expect =
          new PlaylistDto(playlistId, ownerSummary, "제목", "설명", Instant.now(), 0L, true, List.of());

      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));
      given(userRepository.findByIdAndDeletedAtIsNull(ownerId)).willReturn(Optional.of(owner));
      given(playlistMapper.toUserSummary(owner)).willReturn(ownerSummary);
      given(playlistContentRepository.findByPlaylistIdOrderByCreatedAtAscIdAsc(playlistId))
          .willReturn(List.of(new PlaylistContent(playlist, contentId)));
      given(tagRepository.findByContentIdInAndDeletedAtIsNull(List.of(contentId)))
          .willReturn(List.of(tag));
      given(contentRepository.findByIdInAndDeletedAtIsNull(List.of(contentId)))
          .willReturn(List.of(content));
      given(
              subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
                  requesterId, playlistId))
          .willReturn(true);
      ContentSummary contentSummary =
          new ContentSummary(
              contentId, ContentType.MOVIE, "영화", "설명", "http://img", List.of("액션"), 0.0, 0);
      given(playlistMapper.toContentSummary(eq(content), any())).willReturn(contentSummary);
      given(playlistMapper.toDto(eq(playlist), eq(ownerSummary), any(), eq(true)))
          .willReturn(expect);

      // when
      PlaylistDto actual = playlistService.get(playlistId, requesterId);

      // then
      assertThat(actual).isEqualTo(expect);
      then(playlistMapper).should().toContentSummary(eq(content), any());
      // 조립된 콘텐츠 요약이 순서대로 toDto로 전달되는지 확인
      then(playlistMapper)
          .should()
          .toDto(eq(playlist), eq(ownerSummary), eq(List.of(contentSummary)), eq(true));
    }

    @Test
    @DisplayName("논리 삭제되었거나 없는 플레이리스트면 PLAYLIST_NOT_FOUND 예외를 던진다")
    void fail_whenPlaylistNotFound() {
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId)).willReturn(Optional.empty());

      assertThatThrownBy(() -> playlistService.get(playlistId, requesterId))
          .isInstanceOf(PlaylistNotFoundException.class);
    }
  }

  @Nested
  class GetPlaylists {
    private final UUID ownerId = UUID.randomUUID();
    private final UUID requesterId = UUID.randomUUID();

    @Test
    @DisplayName("limit + 1건을 조회해 hasNext를 판정하고 다음 커서를 계산한다")
    void success_computesHasNextAndCursor() {
      // given: limit 1 요청 -> 2건 조회되면 hasNext=true, 첫 1건만 응답
      Playlist first = new Playlist(ownerId, "첫 번째", "설명");
      ReflectionTestUtils.setField(first, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(first, "updatedAt", Instant.parse("2026-06-29T00:00:00Z"));
      Playlist second = new Playlist(ownerId, "두 번째", "설명");
      ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(second, "updatedAt", Instant.parse("2026-06-28T00:00:00Z"));

      PlaylistSearchRequest request =
          new PlaylistSearchRequest(null, null, null, 1, SortDirection.DESCENDING, null);

      given(
              playlistRepository.findPlaylistsByCursor(
                  null, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING, null, null, 2))
          .willReturn(List.of(first, second));
      given(playlistRepository.countActive(null)).willReturn(2L);
      given(userRepository.findAllById(List.of(ownerId))).willReturn(List.of());
      given(
              playlistContentRepository.findByPlaylistIdInOrderByCreatedAtAscIdAsc(
                  List.of(first.getId())))
          .willReturn(List.of());
      given(subscriptionRepository.findSubscribedPlaylistIds(requesterId, List.of(first.getId())))
          .willReturn(List.of());
      given(playlistMapper.toDto(eq(first), any(), any(), eq(false))).willReturn(null);

      // when
      CursorResponse<PlaylistDto> response = playlistService.getPlaylists(request, requesterId);

      // then
      assertThat(response.hasNext()).isTrue();
      assertThat(response.totalCount()).isEqualTo(2L);
      assertThat(response.data()).hasSize(1);
      assertThat(response.nextIdAfter()).isEqualTo(first.getId());
      assertThat(response.nextCursor()).isEqualTo("2026-06-29T00:00:00Z");
      assertThat(response.sortBy()).isEqualTo(PlaylistSortBy.UPDATED_AT.name());
    }

    @Test
    @DisplayName("결과가 없으면 hasNext=false, 데이터가 비어 있고 다음 커서는 null이다")
    void success_whenEmpty() {
      PlaylistSearchRequest request =
          new PlaylistSearchRequest("없는키워드", null, null, 20, SortDirection.DESCENDING, null);

      given(
              playlistRepository.findPlaylistsByCursor(
                  "없는키워드", PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING, null, null, 21))
          .willReturn(List.of());
      given(playlistRepository.countActive("없는키워드")).willReturn(0L);

      CursorResponse<PlaylistDto> response = playlistService.getPlaylists(request, requesterId);

      assertThat(response.hasNext()).isFalse();
      assertThat(response.data()).isEmpty();
      assertThat(response.nextCursor()).isNull();
      assertThat(response.nextIdAfter()).isNull();
    }

    @Test
    @DisplayName("SUBSCRIBE_COUNT 정렬 시 cursor·idAfter를 그대로 전달하고 다음 커서를 구독자 수로 인코딩한다")
    void success_encodesSubscriberCountCursorAndPassesRequest() {
      // given: subscriberCount 정렬 + 커서("5"/cursorId)가 리포지토리로 그대로 전달되는지 검증
      Playlist first = new Playlist(ownerId, "인기", "설명");
      ReflectionTestUtils.setField(first, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(first, "subscriberCount", 42L);
      Playlist second = new Playlist(ownerId, "덜 인기", "설명");
      ReflectionTestUtils.setField(second, "id", UUID.randomUUID());
      ReflectionTestUtils.setField(second, "subscriberCount", 10L);

      UUID cursorId = UUID.randomUUID();
      PlaylistSearchRequest request =
          new PlaylistSearchRequest(
              null, "5", cursorId, 1, SortDirection.DESCENDING, PlaylistSortBy.SUBSCRIBE_COUNT);

      given(
              playlistRepository.findPlaylistsByCursor(
                  null, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.DESCENDING, "5", cursorId, 2))
          .willReturn(List.of(first, second));
      given(playlistRepository.countActive(null)).willReturn(2L);
      given(userRepository.findAllById(List.of(ownerId))).willReturn(List.of());
      given(
              playlistContentRepository.findByPlaylistIdInOrderByCreatedAtAscIdAsc(
                  List.of(first.getId())))
          .willReturn(List.of());
      given(subscriptionRepository.findSubscribedPlaylistIds(requesterId, List.of(first.getId())))
          .willReturn(List.of());
      given(playlistMapper.toDto(eq(first), any(), any(), eq(false))).willReturn(null);

      // when
      CursorResponse<PlaylistDto> response = playlistService.getPlaylists(request, requesterId);

      // then: 다음 커서는 마지막 응답 항목(first)의 subscriberCount 문자열이어야 한다
      assertThat(response.hasNext()).isTrue();
      assertThat(response.nextCursor()).isEqualTo("42");
      assertThat(response.nextIdAfter()).isEqualTo(first.getId());
      assertThat(response.sortBy()).isEqualTo(PlaylistSortBy.SUBSCRIBE_COUNT.name());
    }
  }

  @Nested
  class Delete {
    private final UUID playlistId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Test
    @DisplayName("소유자가 요청하면 플레이리스트와 구독을 소프트 삭제한다")
    void success_whenRequesterIsOwner() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      Subscription subscription1 = new Subscription(UUID.randomUUID(), playlist);
      Subscription subscription2 = new Subscription(UUID.randomUUID(), playlist);
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));
      given(subscriptionRepository.findByPlaylist_IdAndDeletedAtIsNull(playlistId))
          .willReturn(List.of(subscription1, subscription2));

      // when
      playlistService.delete(playlistId, ownerId);

      // then
      assertThat(playlist.isDeleted()).isTrue();
      assertThat(subscription1.isDeleted()).isTrue();
      assertThat(subscription2.isDeleted()).isTrue();
      then(playlistRepository).should().flush();
    }

    @Test
    @DisplayName("플레이리스트가 없으면 PLAYLIST_NOT_FOUND 예외를 던지고 구독을 조회하지 않는다")
    void fail_whenPlaylistNotFound() {
      // given
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> playlistService.delete(playlistId, ownerId))
          .isInstanceOf(PlaylistNotFoundException.class);
      then(subscriptionRepository).should(never()).findByPlaylist_IdAndDeletedAtIsNull(any());
      then(playlistRepository).should(never()).flush();
    }

    @Test
    @DisplayName("요청자가 소유자가 아니면 FORBIDDEN 예외를 던지고 구독을 조회하지 않는다")
    void fail_whenRequesterIsNotOwner() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      UUID otherUserId = UUID.randomUUID();
      given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
          .willReturn(Optional.of(playlist));

      // when & then
      assertThatThrownBy(() -> playlistService.delete(playlistId, otherUserId))
          .isInstanceOf(PlaylistForbiddenException.class);
      assertThat(playlist.isDeleted()).isFalse();
      then(subscriptionRepository).should(never()).findByPlaylist_IdAndDeletedAtIsNull(any());
      then(playlistRepository).should(never()).flush();
    }
  }
}
