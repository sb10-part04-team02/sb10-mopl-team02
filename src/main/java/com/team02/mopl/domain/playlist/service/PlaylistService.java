package com.team02.mopl.domain.playlist.service;

import com.team02.mopl.domain.follow.entity.Follow;
import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.dto.PlaylistUpdateRequest;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.exception.PlaylistForbiddenException;
import com.team02.mopl.domain.playlist.exception.PlaylistNotFoundException;
import com.team02.mopl.domain.playlist.mapper.PlaylistMapper;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.subscription.entity.Subscription;
import com.team02.mopl.domain.subscription.repository.SubscriptionRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
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
  private final SubscriptionRepository subscriptionRepository;
  private final PlaylistMapper playlistMapper;
  private final FollowRepository followRepository;
  private final NotificationService notificationService;
  private final UserRepository userRepository;

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
}
