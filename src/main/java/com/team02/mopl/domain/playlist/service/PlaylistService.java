package com.team02.mopl.domain.playlist.service;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
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
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

  private final PlaylistRepository playlistRepository;
  private final PlaylistContentRepository playlistContentRepository;
  private final ContentRepository contentRepository;
  private final TagRepository tagRepository;
  private final UserRepository userRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final PlaylistMapper playlistMapper;
  private final FollowRepository followRepository;
  private final NotificationService notificationService;

  // 플레이리스트 단건 조회 (소유자·포함 콘텐츠·구독자 수·요청자 구독 여부 포함, 논리 삭제 제외)
  public PlaylistDto get(UUID playlistId, UUID requesterId) {
    log.debug("플레이리스트 단건 조회 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    Playlist playlist =
        playlistRepository
            .findByIdAndDeletedAtIsNull(playlistId)
            .orElseThrow(PlaylistNotFoundException::new);

    UserSummary owner = toOwnerSummary(playlist.getOwnerId());
    List<ContentSummary> contents = toContentSummaries(playlist.getId());
    boolean subscribedByMe =
        subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
            requesterId, playlist.getId());

    return playlistMapper.toDto(playlist, owner, contents, subscribedByMe);
  }

  // 플레이리스트 목록 조회 (커서 페이지네이션, 제목·설명 부분일치 검색)
  public CursorResponse<PlaylistDto> getPlaylists(PlaylistSearchRequest request, UUID requesterId) {
    int limit = CursorPageRequest.normalizeLimit(request.limit());
    SortDirection direction = CursorPageRequest.normalizeSortDirection(request.sortDirection());
    PlaylistSortBy sortBy = request.sortBy() != null ? request.sortBy() : PlaylistSortBy.UPDATED_AT;
    String keyword =
        (request.keyword() != null && !request.keyword().isBlank())
            ? request.keyword().trim()
            : null;

    // hasNext 판정을 위해 limit + 1건을 조회
    List<Playlist> playlists =
        playlistRepository.findPlaylistsByCursor(
            keyword, sortBy, direction, request.cursor(), request.idAfter(), limit + 1);

    boolean hasNext = playlists.size() > limit;
    List<Playlist> page = hasNext ? playlists.subList(0, limit) : playlists;

    List<PlaylistDto> data = toDtos(page, requesterId);
    long totalCount = playlistRepository.countActive(keyword);

    String nextCursor = null;
    UUID nextIdAfter = null;
    if (hasNext) {
      Playlist last = page.get(page.size() - 1);
      nextCursor = encodeCursor(sortBy, last);
      nextIdAfter = last.getId();
    }

    // TODO: 응답 sortBy를 sortBy.name()(UPDATED_AT/SUBSCRIBE_COUNT)로 내리고 있으나 명세 정렬 값은
    //  updatedAt|subscribeCount 이다. 기존 content/review 도메인도 sortBy.name()을 그대로 쓰고 있어
    //  일관성을 위해 현재 형태를 유지한다. 추후 명세 값 매핑 방식을 팀 차원에서 일괄 정리 필요.
    return new CursorResponse<>(
        data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy.name(), direction.name());
  }

  @Transactional
  public PlaylistDto create(UUID ownerId, PlaylistCreateRequest request) {
    log.debug("플레이리스트 생성 시작: ownerId={}", ownerId);

    User owner = getActiveUser(ownerId);

    Playlist playlist = new Playlist(ownerId, request.title(), request.description());
    Playlist saved = playlistRepository.save(playlist);

    // 유저가 플레이리스트 생성 시 본인 팔로우한 사용자에게 알림
    sendFollowingUserActivityNotifications(owner, saved);

    // 방금 생성한 본인 플레이리스트이므로 subscribedByMe는 false
    PlaylistDto playlistDto = playlistMapper.toDto(saved, false);

    log.info("플레이리스트 생성 성공: playlistId={}, ownerId={}", playlistDto.id(), ownerId);
    return playlistDto;
  }

  @Transactional
  public PlaylistDto update(UUID playlistId, UUID requesterId, PlaylistUpdateRequest request) {
    log.debug("플레이리스트 수정 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    Playlist playlist =
        playlistRepository
            .findByIdAndDeletedAtIsNull(playlistId)
            .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwnerId().equals(requesterId)) {
      throw new PlaylistForbiddenException();
    }

    playlist.update(request.title(), request.description());
    playlistRepository.flush();
    // 소유자 본인의 플레이리스트이므로 subscribedByMe는 false
    PlaylistDto playlistDto = playlistMapper.toDto(playlist, false);

    log.info("플레이리스트 수정 성공: playlistId={}, requesterId={}", playlistId, requesterId);
    return playlistDto;
  }

  private void sendFollowingUserActivityNotifications(User owner, Playlist playlist) {
    List<Follow> followers = followRepository.findByFollowee_IdAndDeletedAtIsNull(owner.getId());

    for (Follow follow : followers) {
      notificationService.createNotification(
          new NotificationCreateCommand(
              follow.getFollower().getId(),
              owner.getName() + "님이 플레이리스트를 만들었어요.",
              "[" + playlist.getTitle() + "] " + playlist.getDescription(),
              NotificationLevel.INFO,
              NotificationType.FOLLOWING_USER_ACTIVITY));
    }
  }

  private User getActiveUser(UUID userId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }

  @Transactional
  public void delete(UUID playlistId, UUID requesterId) {
    log.debug("플레이리스트 삭제 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    Playlist playlist =
        playlistRepository
            .findByIdAndDeletedAtIsNull(playlistId)
            .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwnerId().equals(requesterId)) {
      throw new PlaylistForbiddenException();
    }

    playlist.delete();

    List<Subscription> subscriptions =
        subscriptionRepository.findByPlaylist_IdAndDeletedAtIsNull(playlistId);
    subscriptions.forEach(Subscription::delete);

    playlistRepository.flush();

    log.info(
        "플레이리스트 삭제 성공: playlistId={}, requesterId={}, deletedSubscriptionCount={}",
        playlistId,
        requesterId,
        subscriptions.size());
  }

  // ===

  // 정렬값을 원문 문자열로 인코딩 (updatedAt: ISO-8601, subscriberCount: 숫자)
  private String encodeCursor(PlaylistSortBy sortBy, Playlist playlist) {
    return sortBy == PlaylistSortBy.SUBSCRIBE_COUNT
        ? Long.toString(playlist.getSubscriberCount())
        : playlist.getUpdatedAt().toString();
  }

  // 소유자 요약. 소유자가 논리 삭제되어 없으면 id만 담은 요약으로 대체
  private UserSummary toOwnerSummary(UUID ownerId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(ownerId)
        .map(playlistMapper::toUserSummary)
        .orElseGet(() -> new UserSummary(ownerId, null, null));
  }

  // 단건용: 한 플레이리스트의 포함 콘텐츠를 ContentSummary로 조립 (추가된 순서 보존)
  private List<ContentSummary> toContentSummaries(UUID playlistId) {
    List<UUID> contentIds =
        playlistContentRepository.findByPlaylistIdOrderByCreatedAtAscIdAsc(playlistId).stream()
            .map(PlaylistContent::getContentId)
            .toList();
    if (contentIds.isEmpty()) {
      return List.of();
    }
    Map<UUID, ContentSummary> summaryByContent = findContentSummaries(contentIds);
    // 논리 삭제된 콘텐츠는 Map에 없으므로 제외하고, contentIds 순서대로 매핑
    return contentIds.stream().map(summaryByContent::get).filter(Objects::nonNull).toList();
  }

  // 목록용: 페이지에 속한 플레이리스트들을 owner/contents/subscribedByMe까지 일괄 조립 (N+1 방지)
  private List<PlaylistDto> toDtos(List<Playlist> playlists, UUID requesterId) {
    if (playlists.isEmpty()) {
      return List.of();
    }
    List<UUID> playlistIds = playlists.stream().map(Playlist::getId).toList();

    // 소유자 일괄 조회
    List<UUID> ownerIds = playlists.stream().map(Playlist::getOwnerId).distinct().toList();
    Map<UUID, UserSummary> ownerById =
        userRepository.findAllById(ownerIds).stream()
            .filter(user -> !user.isDeleted())
            .collect(Collectors.toMap(User::getId, playlistMapper::toUserSummary));

    // 플레이리스트별 콘텐츠 매핑 일괄 조회(추가된 순서) 후 콘텐츠 요약 조립.
    // 아래 groupingBy는 스트림 순서를 보존하므로 플레이리스트별 콘텐츠 노출 순서가 고정된다.
    List<PlaylistContent> playlistContents =
        playlistContentRepository.findByPlaylistIdInOrderByCreatedAtAscIdAsc(playlistIds);
    List<UUID> allContentIds =
        playlistContents.stream().map(PlaylistContent::getContentId).distinct().toList();
    Map<UUID, ContentSummary> summaryByContent = findContentSummaries(allContentIds);
    Map<UUID, List<ContentSummary>> contentsByPlaylist =
        playlistContents.stream()
            .filter(pc -> summaryByContent.containsKey(pc.getContentId()))
            .collect(
                Collectors.groupingBy(
                    pc -> pc.getPlaylist().getId(),
                    Collectors.mapping(
                        pc -> summaryByContent.get(pc.getContentId()), Collectors.toList())));

    // 요청자가 구독 중인 플레이리스트 일괄 조회
    Set<UUID> subscribedIds =
        Set.copyOf(subscriptionRepository.findSubscribedPlaylistIds(requesterId, playlistIds));

    return playlists.stream()
        .map(
            playlist ->
                playlistMapper.toDto(
                    playlist,
                    ownerById.getOrDefault(
                        playlist.getOwnerId(), new UserSummary(playlist.getOwnerId(), null, null)),
                    contentsByPlaylist.getOrDefault(playlist.getId(), List.of()),
                    subscribedIds.contains(playlist.getId())))
        .toList();
  }

  // 콘텐츠 id 목록으로 ContentSummary를 조립해 id별 Map으로 반환 (논리 삭제 콘텐츠 제외)
  private Map<UUID, ContentSummary> findContentSummaries(List<UUID> contentIds) {
    if (contentIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<Tag>> tagsByContent = findTagsByContent(contentIds);
    return contentRepository.findByIdInAndDeletedAtIsNull(contentIds).stream()
        .collect(
            Collectors.toMap(
                Content::getId,
                content ->
                    playlistMapper.toContentSummary(content, tagsByContent.get(content.getId()))));
  }

  private Map<UUID, List<Tag>> findTagsByContent(List<UUID> contentIds) {
    return tagRepository.findByContentIdInAndDeletedAtIsNull(contentIds).stream()
        .collect(Collectors.groupingBy(tag -> tag.getContent().getId()));
  }
}
